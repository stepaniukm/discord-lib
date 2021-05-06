package example

import akka.actor.ActorSystem
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._

import scala.concurrent.Future

package object WebsocketClientTypes {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
}

case class WebsocketClientConfig(
  socketUrl: String,
  sink: WebsocketClientTypes.WebsocketMessageSink,
);

class WebsocketClient(config: WebsocketClientConfig) {
  def run(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    import system.dispatcher

    val helloSource: Source[Message, NotUsed] =
      Source.single(TextMessage("hello world!"))

    // the Future[Done] is the materialized value of Sink.foreach
    // and it is completed when the stream completes
    val flow: Flow[Message, Message, Future[Done]] =
    Flow.fromSinkAndSourceMat(config.sink, helloSource)(Keep.left)

    // upgradeResponse is a Future[WebSocketUpgradeResponse] that
    // completes or fails when the connection succeeds or fails
    // and closed is a Future[Done] representing the stream completion from above
    val (upgradeResponse, closed) =
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
    closed.foreach(_ => println("closed"))
  }
}
