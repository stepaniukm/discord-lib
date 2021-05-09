package client

import client.domain.{Channel, ChannelFormat}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpMethods}
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Client(val token: String) {

  import ChannelFormat.channelFormat

  implicit val system = ActorSystem("http-client")
  implicit val materializer = ActorMaterializer()

  new DiscordWebsocketClient(
    DiscordWebsocketClientConfig(
      token = token
    )
  ).run();

  val authHeader = RawHeader("Authorization", f"Bot $token");

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

  Http().shutdownAllConnectionPools().foreach(_ => system.terminate);
}
