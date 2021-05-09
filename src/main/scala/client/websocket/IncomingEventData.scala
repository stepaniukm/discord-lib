package client.websocket

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, JsValue, JsObject}

import client.domain.{User, UserFormat, UnavailableGuild, UnavailableGuildFormat}

sealed trait IncomingEventData

case class HelloEventData(heartbeat_interval: Int) extends IncomingEventData;

case class ReadyEventData(v: Int, user: User, guilds: List[UnavailableGuild], session_id: String, shard: Option[(Int, Int)]) extends IncomingEventData

object IncomingEventDataFormat {
  import UserFormat.userFormat;
  import UnavailableGuildFormat.unavailableGuildFormat;

  implicit val helloEventDataFormat = jsonFormat1(HelloEventData)
  implicit val readyEventDataFormat = jsonFormat5(ReadyEventData)

  implicit val incomingEventDataFormat = new JsonFormat[IncomingEventData] {
    override def write(obj: IncomingEventData): JsValue = obj match {
      case obj: HelloEventData => obj.toJson
      case obj: ReadyEventData => obj.toJson
    }

    override def read(json: JsValue): IncomingEventData = {
      val j = json.asJsObject;

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
