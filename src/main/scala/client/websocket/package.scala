package client

package object websocket {
  implicit val outgoingDiscordMessageFormat = OutgoingDiscordMessageFormat.outgoingDiscordMessageFormat;
  implicit val incomingDiscordMessageFormat = IncomingDiscordMessageFormat.incomingDiscordMessageFormat;
}
