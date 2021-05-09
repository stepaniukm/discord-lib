package client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.Sink
import client.WebsocketClientTypes.WebsocketMessageSinkFactory
import client.websocket.{HelloEventData, IdentityEventData, IdentityProperties, IncomingDiscordMessage, OutgoingDiscordMessage, ReadyEventData, outgoingDiscordMessageFormat}

import scala.concurrent.Future
import scala.concurrent.duration._

case class DiscordWebsocketClientConfig(
  gatewayUrl: String = "wss://gateway.discord.gg/?v=9&encoding=json",
  token: String
)

class DiscordWebsocketClient(config: DiscordWebsocketClientConfig) {
  type SendMessageFn = (OutgoingDiscordMessage) => Unit;

  implicit val system: ActorSystem = ActorSystem("websocket")

  import system.dispatcher

  def createOutgoingPayloadHeartbeat(lastSeenSequenceNumber: Option[Int]) =
    OutgoingDiscordMessage(op = 1) // TODO: Pass lastSeenSequenceNumber

  def createIdentityPayload() = OutgoingDiscordMessage(
    op = 2,
    d = Right(
      Some(
        IdentityEventData(
          token = config.token,
          intents = 32767,
          properties = IdentityProperties(
            $os = "Windows",
            $browser = "disco",
            $device = "disco"
          ),
          shard = Some((0, 1)),
          compress = Some(false),
          large_threshold = Some(250)
        )
      )
    )
  )

  def createHeartbeatScheduler(milliseconds: Int)(func: => Unit) = {
    system.scheduler.scheduleAtFixedRate(
      0.seconds,
      milliseconds.milliseconds
    ) { () => {
      println("scheduling")
      func
    }
    }
  }

  def onDiscordMessage(
    sendMessage: SendMessageFn
  )(message: IncomingDiscordMessage) = {
    message.d.map { (optionalData) =>
      optionalData.map {
        case ReadyEventData(v, user, guilds, session_id, shard) => {
          println("##############################################")
          println("I'm ready!")
          println("##############################################")
        }
        case HelloEventData(heartbeat_interval) => {
          createHeartbeatScheduler(heartbeat_interval) {
            sendMessage(createOutgoingPayloadHeartbeat(None));
          }

          sendMessage(createIdentityPayload())
        }
      }
    }
  }

  val messageSinkFactory: WebsocketMessageSinkFactory = (queue) => Sink.foreach {
    case message: TextMessage.Strict => {
      Debug.log("Received raw:", message);
      val parsed = Unmarshal(message.text).to[IncomingDiscordMessage];
      Debug.log("Received parsed:", parsed);

      val offerDebugLog = (m: OutgoingDiscordMessage, f: Future[QueueOfferResult]) => {
        val eventType = m.d.fold(
          l => s"String(${l})",
          r => r.getOrElse(None).getClass.getName
        );
        f.map {
          case QueueOfferResult.Enqueued =>
            Debug.log(s"${eventType} enqueued")
          case QueueOfferResult.Dropped =>
            Debug.log(s"${eventType} dropped")
          case QueueOfferResult.Failure(ex) =>
            Debug.log(s"${eventType} Offer failed ${ex.getMessage}")
          case QueueOfferResult.QueueClosed =>
            Debug.log(s"${eventType} Source Queue closed")
        }
      }

      val sendMessageFn: SendMessageFn = (outgoingDiscordMessage) => {
        val jsonString = outgoingDiscordMessageFormat.write(outgoingDiscordMessage).toString();
        val message = TextMessage(jsonString)

        val future = queue.offer(message)
        offerDebugLog(outgoingDiscordMessage, future);
      }

      parsed.map(onDiscordMessage(sendMessageFn))
    }
  };

  val websocketClient = new WebsocketClient(WebsocketClientConfig(
    socketUrl = config.gatewayUrl,
    token = config.token,
    messageSinkFactory
  ))

  def run(): Unit = websocketClient.run()
}
