package client.domain

import spray.json.DefaultJsonProtocol._

case class UnavailableGuild (
    id: String,
    unavailable: Boolean
)

object UnavailableGuildFormat {
    implicit val unavailableGuildFormat = jsonFormat2(UnavailableGuild)
}