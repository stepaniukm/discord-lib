package client

import client.websocket.{EventData, IdentityEventData, HelloEventData, EventDataFormat, IdentityProperties}
import akka.actor.ActorSystem
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.QueueOfferResult

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol._

import scala.util.{Failure, Success}
import spray.json._
import akka.http.scaladsl.unmarshalling.Unmarshal

case class DiscordMessage(
    // https://discord.com/developers/docs/topics/gateway
    op: Int,
    d: Either[String, Option[EventData]] = Right(None),
    s: Option[Int] = None,
    t: Option[String] = None
)

case class WebsocketClientConfig(
    socketUrl: String,
    token: String
);

class WebsocketClient(config: WebsocketClientConfig) {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
  import EventDataFormat._

  implicit val discordMessageOutFormat = jsonFormat4(DiscordMessage);

  def createOutgoingPayloadHeartbeat(
      lastSeenSequenceNumber: Option[Int]
  ) =
    DiscordMessage(
      op = 1
    )

  def createIdentityPayload() = DiscordMessage(
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
          large_threshold = Some(250),
        )
      )
    )
  )

  def run(): Unit = {
    implicit val system: ActorSystem = ActorSystem("websocket")
    import system.dispatcher

    val bufferSize = 1000
    val overflowStrategy = akka.stream.OverflowStrategy.backpressure

    // https://stackoverflow.com/a/33415214
    val (queue, source) = Source
      .queue[DiscordMessage](bufferSize, overflowStrategy)
      .map[Message](m => {
        val t = TextMessage(discordMessageOutFormat.write(m).toString())
        print("TEXT MESSAGE: ")
        println(t)
        t
      })
      .preMaterialize();

    def createHeartbeatScheduler(milliseconds: Int) = {
      system.scheduler.scheduleAtFixedRate(
        0.seconds,
        milliseconds.milliseconds
      ) { () =>
        {
          println("scheduling")
          queue.offer(createOutgoingPayloadHeartbeat(None)).map {
            case QueueOfferResult.Enqueued => println(s"enqueued")
            case QueueOfferResult.Dropped  => println(s"dropped")
            case QueueOfferResult.Failure(ex) =>
              println(s"Offer failed ${ex.getMessage}")
            case QueueOfferResult.QueueClosed => println("Source Queue closed")
          }
        }
      }
    }

    val messageSink: WebsocketMessageSink = Sink.foreach {
      case message: TextMessage.Strict =>
        print("RAWEST: ");
        println(message);
        val parsed = Unmarshal(message.text).to[DiscordMessage];

        parsed.map { m =>
          {
            print("RAW MESSAGE: ");
            println(m);
            m match {
              case DiscordMessage(op, d, s, t) => {
                d match {
                  case Right(value) => {
                    value match {
                      case Some(value1) => {
                        value1 match {
                          case HelloEventData(heartbeat_interval) => {
                            createHeartbeatScheduler(heartbeat_interval)
                            queue.offer(createIdentityPayload)
                          }
                        }
                      }
                      case None => {
                        println("Event contains no data")
                      }
                    }
                  }
                  case Left(value) => {
                    println("D:", d)
                  }
                }
              };
              case _ => println("Unknown")
            }
          }
        }

      case _ =>
      // ignore other message types
    };

    // flow to use (note: not re-usable!)
//    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(config.socketUrl))

    val flow =
      Flow.fromSinkAndSourceMat(messageSink, source)((Keep.both))

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
