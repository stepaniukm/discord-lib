package client.domain

import spray.json.DefaultJsonProtocol._

case class Channel(
  id: String,
  `type`: Int,
  guild_id: String,
  position: Option[Int],
  name: Option[String],
  topic: Option[String],
  nsfw: Option[Boolean],
  last_message_id: Option[String],
  bitrate: Option[Int],
  user_limit: Option[Int],
  rate_limit_per_user: Option[Int],
  icon: Option[String],
  owner_id: Option[String],
  application_id: Option[String],
  parent_id: Option[String],
  last_pin_timestamp: Option[String],
  rtc_region: Option[String],
  video_quality_mode: Option[Int],
  message_count: Option[Int],
  member_count: Option[Int]
);

object ChannelFormat {
    implicit val channelFormat = jsonFormat20(Channel);
}

