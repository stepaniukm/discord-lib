package client.domain

import spray.json.DefaultJsonProtocol._

case class Emoji (
    id: String,
    name: String,
    roles: Option[List[Role]],
    user: Option[User],
    require_colons: Option[Boolean],
    managed: Option[Boolean],
    animated: Option[Boolean],
    available: Option[Boolean]
)

object EmojiFormat {
    import RoleFormat.roleFormat;
    import UserFormat.userFormat;

    implicit val emojiFormat = jsonFormat8(Emoji)
}