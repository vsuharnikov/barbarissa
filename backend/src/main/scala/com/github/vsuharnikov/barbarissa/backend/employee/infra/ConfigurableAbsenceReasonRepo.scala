package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{AbsenceReason, AbsenceReasonRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import zio.{ZIO, ZLayer}

object ConfigurableAbsenceReasonRepo {
  case class Config(xs: List[AbsenceReason]) {
    val reasons = xs.groupBy(_.id).view.mapValues(_.head).toMap
  }

  val live = ZLayer.fromFunction[Config, AbsenceReasonRepo.Service] { config =>
    new AbsenceReasonRepo.Service {
      override def get(by: AbsenceReasonId): ZIO[Any, error.RepoError, AbsenceReason] =
        config.reasons.get(by) match {
          case Some(value) => ZIO.succeed(value)
          case None        => ZIO.fail(error.RepoRecordNotFound)
        }
    }
  }
}
