package io.reactivecqrs.core

import akka.actor.{ActorRef, Actor}
import akka.actor.Actor.Receive
import io.reactivecqrs.api.Event
import io.reactivecqrs.core.EventsBusActor.{PublishEventsAck, PublishEvents}
import io.reactivecqrs.core.api.{IdentifiableEvent, EventIdentifier}

object EventsBusActor {

  case class PublishEvents[AGGREGATE_ROOT](events: Seq[IdentifiableEvent[AGGREGATE_ROOT]])
  case class PublishEventsAck(eventsIds: Seq[EventIdentifier])
}


class EventsBusActor extends Actor {

  override def receive: Receive = {
    case PublishEvents(events) => handlePublishEvents(sender(), events)
  }

  def handlePublishEvents(respondTo: ActorRef, events: Seq[IdentifiableEvent[Any]]): Unit = {
    respondTo ! PublishEventsAck(events.map(event => EventIdentifier(event.aggregateId, event.version)))
  }

}
