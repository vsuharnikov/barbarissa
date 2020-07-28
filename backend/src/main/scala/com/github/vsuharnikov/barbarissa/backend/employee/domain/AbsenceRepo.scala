package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{MultipleResultsCursor, error}
import zio.ZIO
import zio.macros.accessible

@accessible
object AbsenceRepo {
  trait Service {
    // TODO ZStream? Probably, if we know what we need to do with the end
    def get(by: EmployeeId, cursor: Option[MultipleResultsCursor]): ZIO[Any, error.RepoError, (List[Absence], Option[MultipleResultsCursor])]
    def get(by: EmployeeId, absenceId: AbsenceId): ZIO[Any, error.RepoError, Absence]
    def getAfter(absenceId: AbsenceId,
                 cursor: Option[MultipleResultsCursor]): ZIO[Any, error.RepoError, (List[Absence], Option[MultipleResultsCursor])]
  }
}
