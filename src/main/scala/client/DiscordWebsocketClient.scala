package client

import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.scaladsl.Sink
import client.DiscordMessageConsumerActor.ReceiveMessage
import client.WebsocketClientTypes.WebsocketMessageSinkFactory
import client.websocket.{IncomingDiscordMessage, OutgoingDiscordMessage}


case class DiscordWebsocketClientConfig(
  gatewayUrl: String = "wss://gateway.discord.gg/?v=9&encoding=json",
  token: String,
  scaling: DiscordWebsocketClientScalingConfig = DiscordWebsocketClientScalingConfig()
)

case class DiscordWebsocketClientScalingConfig(
    consumerInstances: Int = 4
)

class DiscordWebsocketClient(config: DiscordWebsocketClientConfig) {
  type SendMessageFn = (OutgoingDiscordMessage) => Unit;

  implicit val system = akka.actor.ActorSystem("websocket")

  import system.dispatcher

  var messageConsumerActor: ActorRef[DiscordMessageConsumerActor.ReceiveMessage] = _;

  val messageSinkFactory: WebsocketMessageSinkFactory = (_) => Sink.foreach {
    case message: TextMessage.Strict =>
      Debug.log("Received raw:", message);
      val parsed = Unmarshal(message.text).to[IncomingDiscordMessage];
      Debug.log("Received parsed:", parsed);

      parsed.map(p => messageConsumerActor ! ReceiveMessage(p))
  };

  val websocketClient = new WebsocketClient(WebsocketClientConfig(
    socketUrl = config.gatewayUrl,
    token = config.token,
    messageSinkFactory
  ))

  def run(): Unit = {
    websocketClient.run()

    // careful, we're using akka Typed as opposed to Classic above
    akka.actor.typed.ActorSystem(createActors(), "discord-websocket-processing");
  }

  def createActors(): Behavior[NotUsed] =
    Behaviors.setup { ctx =>
      val messageQueueActor = ctx.spawn(DiscordMessageQueueActor(this.websocketClient.queue), "messageQueueActor")

      val messageConsumerActorPool = Routers.pool(poolSize = config.scaling.consumerInstances) {
        // make sure the workers are restarted if they fail
        val actor = DiscordMessageConsumerActor(messageQueueActor, config)
        Behaviors.supervise(actor).onFailure[Exception](SupervisorStrategy.restart)
      }
      val messageConsumerRouter = ctx.spawn(messageConsumerActorPool, "messageConsumerActorPool")

      this.messageConsumerActor = messageConsumerRouter;

      ctx.watch(messageConsumerActor)

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
}
