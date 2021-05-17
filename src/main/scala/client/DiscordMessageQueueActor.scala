package client

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.SourceQueue

final case class EnqueueOutgoingMessage(text: String)

object DiscordMessageQueueActor {
  def apply(queue: SourceQueue[Message]): Behavior[EnqueueOutgoingMessage] = {
    Behaviors.receive { (context, message) =>
      context.log.info("Got {}", message);
      // TODO: Handle errors
      queue.offer(TextMessage(message.text));

      Behaviors.same
    }
  }
}
