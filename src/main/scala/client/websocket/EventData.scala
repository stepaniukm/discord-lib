package client.websocket

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, JsValue, JsObject, JsNumber}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

case class Activity(name: String, `type`: Int);

case class IdentityProperties($os: String, $browser: String, $device: String);

case class UpdatePresence(
    since: Int,
    activities: List[Activity],
    status: String,
    afk: Boolean
)

sealed trait EventData

case class HelloEventData(heartbeat_interval: Int) extends EventData;

case class IdentityEventData(
    token: String,
    intents: Int,
    properties: IdentityProperties,
    compress: Option[Boolean] = None,
    large_threshold: Option[Int] = None,
    shard: Option[Tuple2[Int, Int]] = None,
    presence: Option[UpdatePresence] = None
) extends EventData;

object EventDataFormat {
implicit val helloEventDataFormat = jsonFormat1(HelloEventData)
  implicit val identityPropertiesFormat = jsonFormat3(IdentityProperties)
  implicit val activityFormat = jsonFormat2(Activity)
  implicit val presenceFormat = jsonFormat4(UpdatePresence)

  implicit val eventDataFormat = new JsonFormat[EventData] {
    override def write(obj: EventData): JsValue = obj match {
      case HelloEventData(heartbeat_interval) =>
        JsObject("heartbeat_interval" -> heartbeat_interval.toJson)
      case IdentityEventData(
            token,
            intents,
            properties,
            compress,
            large_threshold,
            shard,
            presence
          ) =>
        JsObject(
          "token" -> token.toJson,
          "intents" -> intents.toJson,
          "properties" -> properties.toJson,
          "compress" -> compress.toJson,
          "large_threshold" -> large_threshold.toJson,
          "shard" -> shard.toJson,
          "presence" -> presence.toJson
        )
    }

    override def read(json: JsValue): EventData =
      json.asJsObject.getFields("heartbeat_interval") match {
        case Seq(JsNumber(value)) => json.convertTo[HelloEventData]
        case _ => {
          throw new RuntimeException(s"Invalid json format: $json")
        }
      }
  }
}