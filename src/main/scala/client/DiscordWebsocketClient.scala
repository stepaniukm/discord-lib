package client

import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.scaladsl.Sink
import client.DiscordMessageConsumerActor.ReceiveMessage
import client.WebsocketClientTypes.WebsocketMessageSinkFactory
import client.websocket.{IncomingDiscordMessage, OutgoingDiscordMessage}


case class DiscordWebsocketClientConfig(
  gatewayUrl: String = "wss://gateway.discord.gg/?v=9&encoding=json",
  token: String
)

class DiscordWebsocketClient(config: DiscordWebsocketClientConfig) {
  type SendMessageFn = (OutgoingDiscordMessage) => Unit;

  implicit val system = akka.actor.ActorSystem("websocket")

  import system.dispatcher

  var messageConsumerActor: ActorRef[DiscordMessageConsumerActor.ReceiveMessage] = _;

  val messageSinkFactory: WebsocketMessageSinkFactory = (queue) => Sink.foreach {
    case message: TextMessage.Strict => {
      Debug.log("Received raw:", message);
      val parsed = Unmarshal(message.text).to[IncomingDiscordMessage];
      Debug.log("Received parsed:", parsed);

      parsed.map(p => messageConsumerActor ! ReceiveMessage(p))
    }
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
    Behaviors.setup { context =>
      val messageQueueActor = context.spawn(DiscordMessageQueueActor(this.websocketClient.queue), "messageQueueActor")
      val messageConsumerActor = context.spawn(DiscordMessageConsumerActor(messageQueueActor, config), "messageConsumerActor")
      this.messageConsumerActor = messageConsumerActor;
      context.watch(messageConsumerActor)

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
}
