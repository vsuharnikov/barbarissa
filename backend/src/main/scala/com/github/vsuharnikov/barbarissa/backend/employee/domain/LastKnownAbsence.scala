package com.github.vsuharnikov.barbarissa.backend.employee.domain

import zio.Task
import zio.macros.accessible

@accessible
object LastKnownAbsence {
  trait Service {
    def refreshed: Task[Unit]
  }
}
