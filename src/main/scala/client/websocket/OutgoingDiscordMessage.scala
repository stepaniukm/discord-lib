package client.websocket

import spray.json.DefaultJsonProtocol._

import OutgoingEventDataFormat.outgoingEventDataFormat;

case class OutgoingDiscordMessage(
  op: Int,
  d: Either[String, Option[OutgoingEventData]] = Right(None),
  s: Option[Int] = None,
  t: Option[String] = None
)

object OutgoingDiscordMessageFormat {
  implicit val outgoingDiscordMessageFormat = jsonFormat4(OutgoingDiscordMessage)
}
