package client.domain

import spray.json.DefaultJsonProtocol._

case class GuildPreview (
    id: String,
    name: String,
    icon: String,
    splash: String,
    discovery_flash: String,
    emojis: List[Emoji],
    features: List[String],
    approximate_member_count: Int,
    approximate_presence_count: Int,
    description: String,
)

object GuildPreviewFormat {
    import EmojiFormat.emojiFormat

    implicit val guildPreviewFormat = jsonFormat10(GuildPreview)
}