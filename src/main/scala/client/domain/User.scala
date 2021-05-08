package client.domain

import spray.json.DefaultJsonProtocol._

case class User (
    id: String,
    username: String,
    discriminator: String,
    avatar: Option[String],
    bot: Option[Boolean],
    system: Option[Boolean],
    mfa_enabled: Option[Boolean],
    locale: Option[String],
    verified: Option[Boolean],
    email: Option[String],
    flags: Option[Int],
    premium_type: Option[Int],
    public_flags: Option[Int]
)

object UserFormat {
    implicit val userFormat = jsonFormat13(User)
}