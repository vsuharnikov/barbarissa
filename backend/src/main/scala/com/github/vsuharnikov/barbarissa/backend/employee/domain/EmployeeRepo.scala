package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{DomainError, EmployeeId}
import zio.{Task, ZIO}
import zio.macros.accessible

@accessible
object EmployeeRepo extends Serializable {
  trait Service extends Serializable {
    def update(draft: Employee): Task[Unit]

    def unsafeGet(by: EmployeeId): Task[Employee] = get(by).flatMap {
      case Some(x) => ZIO.succeed(x)
      case None    => ZIO.fail(DomainError.NotFound("Employee", by.asString))
    }
    def get(by: EmployeeId): Task[Option[Employee]]
    def search(byEmail: String): Task[Option[Employee]]
  }
}
