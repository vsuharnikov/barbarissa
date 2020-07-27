package com.github.vsuharnikov.barbarissa.backend.employee.domain

import zio.Task
import zio.macros.accessible

@accessible
object UnprocessedAbsenceRepo {
  trait Service {
    def get(num: Int): Task[List[UnprocessedAbsence]]
    def update(draft: UnprocessedAbsence): Task[Unit]
  }
}
