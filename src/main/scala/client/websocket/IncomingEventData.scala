package client.websocket

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsonFormat, JsValue, JsObject, JsNumber}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

sealed trait IncomingEventData

case class HelloEventData(heartbeat_interval: Int) extends IncomingEventData;

object IncomingEventDataFormat {
  implicit val helloEventDataFormat = jsonFormat1(HelloEventData)

  implicit val incomingEventDataFormat = new JsonFormat[IncomingEventData] {
    override def write(obj: IncomingEventData): JsValue = obj match {
      case HelloEventData(heartbeat_interval) =>
        JsObject("heartbeat_interval" -> heartbeat_interval.toJson)
    }

    override def read(json: JsValue): IncomingEventData = {
      json.asJsObject.getFields("heartbeat_interval") match {
        case Seq(JsNumber(value)) => json.convertTo[HelloEventData]
        case _ => {
          throw new RuntimeException(s"Invalid json format: $json")
        }
      }
    }
  }
}
