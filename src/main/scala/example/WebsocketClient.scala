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

package object WebsocketClientTypes {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
  type DiscordMessageHandler = (DiscordMessage) => Unit
}

case class DiscordMessage(
  // https://discord.com/developers/docs/topics/gateway
  op: Int,
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
  sink: WebsocketClientTypes.WebsocketMessageSink,
);

class WebsocketClient(config: WebsocketClientConfig) {
  implicit val discordMessageFormat = jsonFormat3(DiscordMessage);

  def createOutgoingPayloadHeartbeat(lastSeenSequenceNumber: Option[Int]): DiscordMessage =
    DiscordMessage(
      op = 1,
      s = lastSeenSequenceNumber
    )

  def run(): Unit = {
    implicit val system: ActorSystem = ActorSystem("websocket")
    import system.dispatcher

    val bufferSize = 1000
    val overflowStrategy = akka.stream.OverflowStrategy.backpressure

    // https://stackoverflow.com/a/33415214
    val (queue, source) = Source
      .queue[DiscordMessage](bufferSize, overflowStrategy)
      .map[Message](m => TextMessage(discordMessageFormat.write(m).toString())
      )
      .preMaterialize()

    system.scheduler.scheduleAtFixedRate(2.seconds, 10.seconds) {
      () => {
        println("scheduling")
        queue.offer(createOutgoingPayloadHeartbeat(None)).map {
          case QueueOfferResult.Enqueued => println(s"enqueued")
          case QueueOfferResult.Dropped => println(s"dropped")
          case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
          case QueueOfferResult.QueueClosed => println("Source Queue closed")
        }
      }
    }

    // flow to use (note: not re-usable!)
//    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(config.socketUrl))

    val flow =
      Flow.fromSinkAndSourceMat(
        config.sink,
        source)((Keep.both))

    val (upgradeResponse, (sinkClose, sourceClose)) =
      Http().singleWebSocketRequest(WebSocketRequest(config.socketUrl), flow)

    val connected = upgradeResponse.map { upgrade =>
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
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
