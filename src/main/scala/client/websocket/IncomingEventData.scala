package client.websocket

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, JsValue, JsObject, JsNumber}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import client.domain.{User, UserFormat, GuildPreview, GuildPreviewFormat}

sealed trait IncomingEventData

case class HelloEventData(heartbeat_interval: Int) extends IncomingEventData;

case class ReadyEventData(v: Int, user: User, guilds: List[GuildPreview], session_id: String, shard: Option[Tuple2[Int, Int]]) extends IncomingEventData

object IncomingEventDataFormat {
  import UserFormat.userFormat;
  import GuildPreviewFormat.guildPreviewFormat;

  implicit val helloEventDataFormat = jsonFormat1(HelloEventData)
  implicit val readyEventDataFormat = jsonFormat5(ReadyEventData)

  implicit val incomingEventDataFormat = new JsonFormat[IncomingEventData] {
    override def write(obj: IncomingEventData): JsValue = obj match {
      case HelloEventData(heartbeat_interval) => JsObject("heartbeat_interval" -> heartbeat_interval.toJson)
      case ReadyEventData(v, user, guilds, session_id, shard) => JsObject(
        "v" -> v.toJson,
        "user" -> user.toJson,
        "guilds" -> guilds.toJson,
        "session_id" -> session_id.toJson,
        "shard" -> shard.toJson,
      )
    }

    override def read(json: JsValue): IncomingEventData = {
      val j = json.asJsObject;

      println("-----------------------------------------------")
      println(j.getFields("v", "user", "guilds", "session_id").size)
      println("-----------------------------------------------")

      if (j.getFields("heartbeat_interval").size == 1) {
        return json.convertTo[HelloEventData]
      } else if (j.getFields("v", "user", "guilds", "session_id").size == 4) {
        println("I'm ready event")
        return json.convertTo[ReadyEventData]
      }

      throw new Exception("Not handled Incoming Message Parsing");
    }
  }
}
