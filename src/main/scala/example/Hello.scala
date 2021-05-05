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
import spray.json.DefaultJsonProtocol._
import spray.json.DefaultJsonProtocol
import akka.stream.scaladsl.Flow
import net.liftweb.json._

case class Channel(
  id: String, 
  last_message_id: String, 
  name: String,
  position: String,
  parent_id: String,
  topic: String,
  guild_id: String,
  nsfw: Boolean,
  rate_limit_per_user: Int
);

object Example extends App {
  implicit val system = ActorSystem("http-client")
  implicit val materializer = ActorMaterializer()
  implicit val formats = DefaultFormats

  val token = env.get("TOKEN");

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
        content <- Unmarshal(response.entity).to[String]
      } yield content

      val result = Await.result(c, 10.seconds);

      val extractedChannel = parse(result).extract[Channel];

      println(extractedChannel.id);

    }
    case None => {
      println("You didn't provide a token");
    }
  }



  Http().shutdownAllConnectionPools().foreach(_ => system.terminate);
}
