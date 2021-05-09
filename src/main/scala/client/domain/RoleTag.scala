package client.domain

import spray.json.DefaultJsonProtocol._

case class RoleTag (
    bot_id: Option[String],
    integration_id: Option[String],
    premium_subscriber: Option[Boolean]
)

object RoleTagFormat {
    implicit val roleTagFormat = jsonFormat3(RoleTag)
}