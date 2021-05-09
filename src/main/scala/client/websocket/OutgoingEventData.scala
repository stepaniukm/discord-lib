package client.websocket

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, JsValue, JsObject}

sealed trait OutgoingEventData

case class Activity(name: String, `type`: Int);

case class UpdatePresence(
    since: Int,
    activities: List[Activity],
    status: String,
    afk: Boolean
)

case class IdentityProperties($os: String, $browser: String, $device: String);

case class IdentityEventData(
    `type`: String = "Identity",
    token: String,
    intents: Int,
    properties: IdentityProperties,
    compress: Option[Boolean] = None,
    large_threshold: Option[Int] = None,
    shard: Option[Tuple2[Int, Int]] = None,
    presence: Option[UpdatePresence] = None
) extends OutgoingEventData;

object OutgoingEventDataFormat {
  implicit val activityFormat = jsonFormat2(Activity)
  implicit val presenceFormat = jsonFormat4(UpdatePresence)
  implicit val identityPropertiesFormat = jsonFormat3(IdentityProperties)
  implicit val identityEventData = jsonFormat8(IdentityEventData)

  implicit val outgoingEventDataFormat = new JsonFormat[OutgoingEventData] {
    override def write(obj: OutgoingEventData): JsValue = obj match {
      case obj: IdentityEventData => obj.toJson
    }

    override def read(json: JsValue): OutgoingEventData = {
      json.asJsObject.getFields("type") match {
        case Seq(JsString("Identity")) => json.convertTo[IdentityEventData]
        case _ => {
          throw new RuntimeException(s"Invalid json format: $json")
        }
      }
    }
  }
}
