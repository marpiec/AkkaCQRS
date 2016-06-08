package io.reactivecqrs.core.saga

import akka.actor.ActorRef
import io.reactivecqrs.api.id.UserId


abstract class SagaState {

  def createSaga(name: String, sagaId: Long, respondTo: String, order: SagaInternalOrder): Unit

  def updateSaga(name: String, sagaId: Long, order: SagaInternalOrder, phase: SagaPhase): Unit

  def deleteSaga(name: String, sagaId: Long): Unit

  def loadAllSagas(handler: (String, Long, UserId, String, SagaPhase, SagaInternalOrder) => Unit): Unit
}



