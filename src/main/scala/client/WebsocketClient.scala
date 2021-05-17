package client

import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.OverflowStrategy

import scala.concurrent.Future

import scala.util.{Failure, Success}

case class WebsocketClientConfig(
  socketUrl: String,
  token: String,
  messageSinkFactory: WebsocketClientTypes.WebsocketMessageSinkFactory
);

object WebsocketQueueConfig {
  val bufferSize = 1000
  val overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure
  val maxConcurrency = 16
}

object WebsocketClientTypes {
  type WebsocketMessageSink = Sink[Message, Future[Done]];
  type WebsocketMessageSinkFactory = (SourceQueue[Message]) => WebsocketMessageSink;
}

class WebsocketClient(config: WebsocketClientConfig) {
  implicit val system: ActorSystem = ActorSystem("websocket")
  import system.dispatcher;

  val (queue, source) = Source
    .queue[Message](
      WebsocketQueueConfig.bufferSize,
      WebsocketQueueConfig.overflowStrategy,
      WebsocketQueueConfig.maxConcurrency
    )
    .preMaterialize();

  def run(): Unit = {
    val sink: WebsocketClientTypes.WebsocketMessageSink = config.messageSinkFactory(queue);
    val flow = Flow.fromSinkAndSourceMat(sink, source)(Keep.both)

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
