package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import zio.ZIO
import zio.macros.accessible

@accessible
object AbsenceRepo {
  trait Service {
    def get(by: EmployeeId): ZIO[Any, error.RepoError, List[Absence]]
    def get(by: EmployeeId, absenceId: AbsenceId): ZIO[Any, error.RepoError, Absence]
    def getAfter(absenceId: AbsenceId, num: Int): ZIO[Any, error.RepoError, Absence]
  }
}
