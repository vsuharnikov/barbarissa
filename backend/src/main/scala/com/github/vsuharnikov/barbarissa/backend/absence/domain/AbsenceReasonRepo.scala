package com.github.vsuharnikov.barbarissa.backend.absence.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceReasonId, DomainError}
import zio.{Task, ZIO}
import zio.macros.accessible

@accessible
object AbsenceReasonRepo extends Serializable {
  trait Service extends Serializable {
    def unsafeGet(by: AbsenceReasonId): Task[AbsenceReason] = get(by).flatMap {
      case Some(x) => ZIO.succeed(x)
      case None    => ZIO.fail(DomainError.NotFound("AbsenceReason", by.asString))
    }
    def get(by: AbsenceReasonId): Task[Option[AbsenceReason]]
    def all: Task[Map[AbsenceReasonId, AbsenceReason]]
  }
}
