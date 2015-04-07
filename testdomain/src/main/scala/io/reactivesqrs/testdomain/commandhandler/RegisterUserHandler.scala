package io.reactivesqrs.testdomain.commandhandler

import io.reactivecqrs.api.command.{FollowingCommandHandler, RepositoryHandler}
import io.reactivecqrs.api.guid.{CommandId, UserId}
import io.reactivesqrs.testdomain.api._


class RegisterUserHandler extends FollowingCommandHandler[User, RegisterUser, RegisterUserResult] {


  override def handle(commandId: CommandId, userId: UserId, aggregateRoot: User, command: RegisterUser, repository: RepositoryHandler[User]): RegisterUserResult = {
    val result = repository.storeFirstEvent(commandId, userId, UserRegistered(command.name))
    RegisterUserResult(success = true, registeredUserId = result.aggregateId)
  }


  override def commandClass: Class[DeleteUser] = classOf[DeleteUser]

}