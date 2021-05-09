package client.websocket

import spray.json.DefaultJsonProtocol._

import IncomingEventDataFormat.incomingEventDataFormat;

case class IncomingDiscordMessage(
  op: Int,
  d: Either[String, Option[IncomingEventData]] = Right(None),
  s: Option[Int] = None,
  t: Option[String] = None
);

object IncomingDiscordMessageFormat {
  implicit val incomingDiscordMessageFormat = jsonFormat4(IncomingDiscordMessage)
}
