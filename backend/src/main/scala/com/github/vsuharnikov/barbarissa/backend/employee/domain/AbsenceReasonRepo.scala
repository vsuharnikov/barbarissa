package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceReasonId
import zio.Task
import zio.macros.accessible

@accessible
object AbsenceReasonRepo extends Serializable {
  trait Service extends Serializable {
    def get(by: AbsenceReasonId): Task[Option[AbsenceReason]]
    def all: Task[Map[AbsenceReasonId, AbsenceReason]]
  }
}
