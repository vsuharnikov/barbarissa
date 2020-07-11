package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.EmployeeId
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import zio.ZIO
import zio.macros.accessible

@accessible
object EmployeeRepo {
  trait Service {
    def update(draft: Employee): ZIO[Any, error.RepoError, Unit]
    def get(by: EmployeeId): ZIO[Any, error.RepoError, Employee]
  }
}
