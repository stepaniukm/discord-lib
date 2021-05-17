package client

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import client.websocket.{HelloEventData, IdentityEventData, IdentityProperties, IncomingDiscordMessage, OutgoingDiscordMessage, ReadyEventData, outgoingDiscordMessageFormat}

object DiscordMessageConsumerActor {
  type SendMessageFn = (OutgoingDiscordMessage) => Unit;

  final case class ReceiveMessage(m: IncomingDiscordMessage)

  def createOutgoingPayloadHeartbeat(lastSeenSequenceNumber: Option[Int]) =
    OutgoingDiscordMessage(op = 1) // TODO: Pass lastSeenSequenceNumber

  def createIdentityPayload(config: DiscordWebsocketClientConfig) = OutgoingDiscordMessage(
    op = 2,
    d = Right(
      Some(
        IdentityEventData(
          token = config.token,
          intents = 32767,
          properties = IdentityProperties(
            $os = "Windows",
            $browser = "disco",
            $device = "disco"
          ),
          shard = Some((0, 1)),
          compress = Some(false),
          large_threshold = Some(250)
        )
      )
    )
  )

  def onDiscordMessage(
    sendMessage: SendMessageFn
  )(message: IncomingDiscordMessage, config: DiscordWebsocketClientConfig) = {
    message.d.map { (optionalData) =>
      optionalData.map {
        case ReadyEventData(v, user, guilds, session_id, shard) => {
          println("##############################################")
          println("I'm ready!")
          println("##############################################")
        }
        case HelloEventData(heartbeat_interval) => {
          sendMessage(createOutgoingPayloadHeartbeat(None));

          sendMessage(createIdentityPayload(config))
        }
      }
    }
  }

  def apply(queue: ActorRef[EnqueueOutgoingMessage], config: DiscordWebsocketClientConfig): Behavior[ReceiveMessage] = Behaviors.receive { (context, message) =>
    Debug.log("DiscordMessageConsumerActor received")

    val sendMessageFn: SendMessageFn = (outgoing: OutgoingDiscordMessage) => {
      val jsonString = outgoingDiscordMessageFormat.write(outgoing).toString();
      queue ! EnqueueOutgoingMessage(jsonString)
    }
    onDiscordMessage(sendMessageFn)(message.m, config)

    Behaviors.same
  }
}
