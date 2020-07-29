package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.EmployeeId
import zio.Task
import zio.macros.accessible

@accessible
object EmployeeRepo {
  trait Service {
    def update(draft: Employee): Task[Unit]
    def get(by: EmployeeId): Task[Option[Employee]]
  }
}
