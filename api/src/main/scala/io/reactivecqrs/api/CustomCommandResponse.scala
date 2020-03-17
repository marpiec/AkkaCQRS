package io.reactivecqrs.api

import io.reactivecqrs.api.id.{CommandId, AggregateId}

import scala.reflect.runtime.universe._

// Command handling default response

object Nothing {
  val empty = Nothing()
}
case class Nothing()

sealed abstract class CustomCommandResponse[INFO: TypeTag]

case class SuccessResponse(aggregateId: AggregateId, aggregateVersion: AggregateVersion) extends CommandResponse
case class CustomSuccessResponse[INFO: TypeTag](aggregateId: AggregateId, aggregateVersion: AggregateVersion, info: INFO) extends CustomCommandResponse[INFO]
case class FailureResponse(exceptions: List[String]) extends CustomCommandResponse[Nothing]
case class AggregateConcurrentModificationError(aggregateId: AggregateId,
                                                aggregateType: AggregateType,
                                                expected: AggregateVersion,
                                                was: AggregateVersion) extends CustomCommandResponse[Nothing]
case class CommandHandlingError(commandName: String, errorId: String, commandId: CommandId) extends CustomCommandResponse[Nothing]
case class EventHandlingError(eventName: String, errorId: String, commandId: CommandId) extends CustomCommandResponse[Nothing]