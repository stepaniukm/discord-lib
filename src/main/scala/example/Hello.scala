package example

import sys.env
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.HttpProtocols
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ws.TextMessage
import spray.json.DefaultJsonProtocol._
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat
import akka.stream.scaladsl.{Flow, Sink}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.model.HttpEntity

case class Channel(
  id: String,
  `type`: Int,
  guild_id: String,
  position: Option[Int],
  name: Option[String],
  topic: Option[String],
  nsfw: Option[Boolean],
  last_message_id: Option[String],
  bitrate: Option[Int],
  user_limit: Option[Int],
  rate_limit_per_user: Option[Int],
  icon: Option[String],
  owner_id: Option[String],
  application_id: Option[String],
  parent_id: Option[String],
  last_pin_timestamp: Option[String],
  rtc_region: Option[String],
  video_quality_mode: Option[Int],
  message_count: Option[Int],
  member_count: Option[Int]
);

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val channelFormat: RootJsonFormat[Channel] = jsonFormat20(Channel.apply);
}

object Example extends App {
  import MyJsonProtocol.channelFormat;

  implicit val system: ActorSystem = ActorSystem("http-client")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val token = env.get("TOKEN");

  val messageSink: WebsocketClientTypes.WebsocketMessageSink = Sink.foreach {
    case message: TextMessage.Strict =>
      println(message.text)
    case _ =>
    // ignore other message types
  };

  new WebsocketClient(
    WebsocketClientConfig(
      "wss://gateway.discord.gg/?v=8&encoding=json",
      sink = messageSink
    )
  ).run();

  token match {
    case Some(value) => {
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
