package example

import sys.env
import client.domain.{Channel, ChannelFormat}
import client.{WebsocketClient, WebsocketClientConfig};
import akka.http.scaladsl.model.headers.{RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpMethods}
import akka.http.scaladsl.{Http}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Example extends App {
  import ChannelFormat.channelFormat
  implicit val system = ActorSystem("http-client")
  implicit val materializer = ActorMaterializer()

  val token = env.get("TOKEN");

  token match {
    case Some(value) => {
      new WebsocketClient(
        WebsocketClientConfig(
          socketUrl = "wss://gateway.discord.gg/?v=9&encoding=json",
          token = value,
        )
      ).run();
      val authHeader = RawHeader("Authorization", f"Bot $value");

      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = "https://discord.com/api/v9/channels/839587771483553855",
        headers = Seq(authHeader)
      );

      val c = for {
        response <- Http().singleRequest(request)
        content <- Unmarshal(response.entity).to[Channel]
      } yield content

      val result = Await.result(c, 10.seconds);

      println(result.`type`);

    }
    case None => {
      println("You didn't provide a token");
    }
  }

  Http().shutdownAllConnectionPools().foreach(_ => system.terminate);
}
