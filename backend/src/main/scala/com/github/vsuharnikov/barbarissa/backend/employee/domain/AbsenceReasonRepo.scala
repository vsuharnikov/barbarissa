package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import zio.ZIO
import zio.macros.accessible

@accessible
object AbsenceReasonRepo extends Serializable {
  trait Service extends Serializable {
    def get(by: AbsenceReasonId): ZIO[Any, error.RepoError, AbsenceReason]
    def all: ZIO[Any, error.RepoError, Map[AbsenceReasonId, AbsenceReason]]
  }
}
