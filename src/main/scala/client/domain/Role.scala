package client.domain

import spray.json.DefaultJsonProtocol._

case class Role (
    id: String,
    name: String,
    color: Int,
    hoist: Boolean,
    position: Int,
    permissions: String,
    managed: Boolean,
    mentionable: Boolean,
    tags: List[RoleTag]
)

object RoleFormat {
    import RoleTagFormat.roleTagFormat

    implicit val roleFormat = jsonFormat9(Role)
}