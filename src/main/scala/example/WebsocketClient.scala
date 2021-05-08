package example

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

package object WebsocketClientTypes {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
  type DiscordMessageHandler = (DiscordMessage) => Unit
}

case class Activity(name: String, `type`: Int);

case class IdentityProperties($os: String, $browser: String, $device: String);

case class UpdatePresence(
    since: Int,
    activities: List[Activity],
    status: String,
    afk: Boolean
)

sealed trait EventData

case class HelloEventData(heartbeat_interval: Int) extends EventData;

case class IdentityEventData(
    token: String,
    intents: Int,
    properties: IdentityProperties,
    compress: Option[Boolean] = None,
    large_threshold: Option[Int] = None,
    shard: Option[Tuple2[Int, Int]] = None,
    presence: Option[UpdatePresence] = None
) extends EventData;

case class DiscordMessage(
    // https://discord.com/developers/docs/topics/gateway
    op: Int,
    d: Either[String, Option[EventData]] = Right(None),
    s: Option[Int] = None,
    t: Option[String] = None
)

case class IncomingPayloadHeartbeat(
    heartbeat_interval: Int
)

case class IncomingPayloadInvalidSession(
    heartbeat_interval: Int
)

case class WebsocketClientConfig(
    socketUrl: String,
    token: String
);

class WebsocketClient(config: WebsocketClientConfig) {
  implicit val helloEventDataFormat = jsonFormat1(HelloEventData)
  implicit val identityPropertiesFormat = jsonFormat3(IdentityProperties)
  implicit val activityFormat = jsonFormat2(Activity)
  implicit val presenceFormat = jsonFormat4(UpdatePresence)

  implicit val eventDataFormat = new JsonFormat[EventData] {
    override def write(obj: EventData): JsValue = obj match {
      case HelloEventData(heartbeat_interval) =>
        JsObject("heartbeat_interval" -> heartbeat_interval.toJson)
      case IdentityEventData(
            token,
            intents,
            properties,
            compress,
            large_threshold,
            shard,
            presence
          ) =>
        JsObject(
          "token" -> token.toJson,
          "intents" -> intents.toJson,
          "properties" -> properties.toJson,
          // "compress" -> compress.toJson,
          // "large_threshold" -> large_threshold.toJson,
          "shard" -> shard.toJson
          // "presence" -> presence.toJson
        )
    }

    override def read(json: JsValue): EventData =
      json.asJsObject.getFields("heartbeat_interval") match {
        case Seq(JsNumber(value)) => json.convertTo[HelloEventData]
        case _ => {
          throw new RuntimeException(s"Invalid json format: $json")
        }
      }
  }

  implicit val discordMessageOutFormat = jsonFormat4(DiscordMessage);

  def createOutgoingPayloadHeartbeat(
      lastSeenSequenceNumber: Option[Int]
  ) =
    DiscordMessage(
      op = 1,
      d = Left("123")
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
          shard = Some((0, 1))
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

    val messageSink: WebsocketClientTypes.WebsocketMessageSink = Sink.foreach {
      case message: TextMessage.Strict =>
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
                            queue.offer(createIdentityPayload)
                            createHeartbeatScheduler(heartbeat_interval)
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
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(
          s"Connection failed: ${upgrade.response.status}"
        )
      }
    }

    // in a real application you would not side effect here
    // and handle errors more carefully
    connected.onComplete(println)
    sinkClose.onComplete {
      case Success(_) => println("Connection closed gracefully")
      case Failure(e) => println(f"Connection closed with an error: $e")
    }
    // closed.asd() TODO: how do we wait for "closed"?
  }
}
