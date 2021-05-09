package client

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.SourceQueue

final case class EnqueueOutgoingMessage(text: String)

object DiscordMessageQueueActor {
  def create(queue: SourceQueue[Message]): Behavior[EnqueueOutgoingMessage] = {
    Behaviors.receive { (context, message) =>
      context.log.info("Got {}", message);
      // TODO: This will probably error if called from multiple threads :(
      queue.offer(TextMessage(message.text));

      Behaviors.same
    }
  }
}
