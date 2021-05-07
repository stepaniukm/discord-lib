package example

import akka.actor.ActorSystem
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol._

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
    implicit val system: ActorSystem = ActorSystem()
    import system.dispatcher

    val bufferSize = 1000
    val overflowStrategy = akka.stream.OverflowStrategy.fail

    // https://stackoverflow.com/a/33415214
    val (queue, source) = Source
      .queue[DiscordMessage](bufferSize, overflowStrategy)
      .map[Message](m => {
        val text = TextMessage(discordMessageFormat.write(m).toString());
        println(text);
        return text;
      })
      .preMaterialize();

    system.scheduler.scheduleAtFixedRate(3.seconds, 1.seconds) {
      () => {
        println("scheduling")
        queue.offer(createOutgoingPayloadHeartbeat(None))
        queue.offer(createOutgoingPayloadHeartbeat(None))
      }
    }

    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(config.socketUrl))

    val (upgradeResponse, closed) =
    source
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(config.sink)(Keep.both) // also keep the Future[Done]
      .run()

    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.foreach(_ => println("closed"))
  }
}
