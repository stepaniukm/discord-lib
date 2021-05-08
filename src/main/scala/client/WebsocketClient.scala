package client

import client.websocket.{
  OutgoingEventDataFormat,
  IncomingEventDataFormat,
  IncomingEventData,
  OutgoingEventData,
  IdentityEventData,
  IdentityProperties,
  HelloEventData,
  ReadyEventData
}
import akka.actor.ActorSystem
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.{QueueOfferResult, OverflowStrategy}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol._

import scala.util.{Failure, Success}
import spray.json._
import akka.http.scaladsl.unmarshalling.Unmarshal

case class IncomingDiscordMessage(
    op: Int,
    d: Either[String, Option[IncomingEventData]] = Right(None),
    s: Option[Int] = None,
    t: Option[String] = None
)

case class OutgoingDiscordMessage(
    op: Int,
    d: Either[String, Option[OutgoingEventData]] = Right(None),
    s: Option[Int] = None,
    t: Option[String] = None
)

case class WebsocketClientConfig(
    socketUrl: String,
    token: String
);

object WebsocketQueueConfig {
  val bufferSize = 1000
  val overflowStrategy = OverflowStrategy.backpressure
}

class WebsocketClient(config: WebsocketClientConfig) {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
  import IncomingEventDataFormat.incomingEventDataFormat;
  import OutgoingEventDataFormat.outgoingEventDataFormat;

  implicit val discordMessageOutFormat = jsonFormat4(OutgoingDiscordMessage);
  implicit val discordMessageInFormat = jsonFormat4(IncomingDiscordMessage);
  implicit val system: ActorSystem = ActorSystem("websocket")
  import system.dispatcher

  def createOutgoingPayloadHeartbeat(lastSeenSequenceNumber: Option[Int]) =
    OutgoingDiscordMessage(op = 1)

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
    ) { () =>
      {
        println("scheduling")
        func
      }
    }
  }

  def onDiscordMessage(
      queue: SourceQueue[OutgoingDiscordMessage]
  )(message: IncomingDiscordMessage) = {
    message.d.map { (optionalData) =>
      optionalData.map { (data) =>
        data match {
          case HelloEventData(heartbeat_interval) => {
            createHeartbeatScheduler(heartbeat_interval) {
              queue.offer(createOutgoingPayloadHeartbeat(None)).map {
                case QueueOfferResult.Enqueued => println(s"enqueued")
                case QueueOfferResult.Dropped  => println(s"dropped")
                case QueueOfferResult.Failure(ex) =>
                  println(s"Offer failed ${ex.getMessage}")
                case QueueOfferResult.QueueClosed =>
                  println("Source Queue closed")
              }
            }
            queue.offer(createIdentityPayload)
          }
          case ReadyEventData(v, user, guilds, session_id, shard) => {
            println("##############################################")
            println("I'm ready!")
            println("##############################################")
          }
        }
      }
    }
  }

  def run(): Unit = {
    val (queue, source) = Source
      .queue[OutgoingDiscordMessage](
        WebsocketQueueConfig.bufferSize,
        WebsocketQueueConfig.overflowStrategy
      )
      .map[Message](m => {
        val t = TextMessage(discordMessageOutFormat.write(m).toString())

        println("SENT: ", t)
        t
      })
      .preMaterialize();

    val messageSink: WebsocketMessageSink = Sink.foreach {
      case message: TextMessage.Strict => {
        print("RAW: ");
        println(message);
        val parsed = Unmarshal(message.text).to[IncomingDiscordMessage];

        parsed.map(onDiscordMessage(queue))
      }
      case m => {
        println("Other message", m)
      }
    };

    val flow = Flow.fromSinkAndSourceMat(messageSink, source)((Keep.both))

    val (upgradeResponse, (sinkClose, _)) =
      Http().singleWebSocketRequest(WebSocketRequest(config.socketUrl), flow)

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(
          s"Connection failed: ${upgrade.response.status}"
        )
      }
    }

    connected.onComplete(println)
    sinkClose.onComplete {
      case Success(_) => println("Connection closed gracefully")
      case Failure(e) => println(f"Connection closed with an error: $e")
    }
  }
}
