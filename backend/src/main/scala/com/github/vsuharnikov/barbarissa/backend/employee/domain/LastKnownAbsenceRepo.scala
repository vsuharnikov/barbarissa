package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId
import zio.Task
import zio.macros.accessible

@accessible
object LastKnownAbsenceRepo {
  trait Service {
    def lastAbsence: Task[Option[AbsenceId]]
    def update(draft: AbsenceId): Task[Unit]
  }
}
