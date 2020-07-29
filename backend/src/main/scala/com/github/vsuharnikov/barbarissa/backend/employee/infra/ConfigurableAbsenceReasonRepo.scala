package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{AbsenceReason, AbsenceReasonRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import zio.config.magnolia.DeriveConfigDescriptor.Descriptor
import zio.{ZIO, ZLayer}

object ConfigurableAbsenceReasonRepo {
  case class Config(xs: List[AbsenceReason]) {
    val reasons = xs.groupBy(_.id).view.mapValues(_.head).toMap
  }

  object Config {
    implicit val configDescriptor: Descriptor[Config] = Descriptor[List[AbsenceReason]].xmap(Config(_), _.xs)
  }

  val live = ZLayer.fromService[Config, AbsenceReasonRepo.Service] { config =>
    new AbsenceReasonRepo.Service {
      override def get(by: AbsenceReasonId): ZIO[Any, error.RepoError, AbsenceReason] =
        config.reasons.get(by) match {
          case Some(value) => ZIO.succeed(value)
          case None        => ZIO.fail(error.RepoRecordNotFound)
        }

      override def all: ZIO[Any, error.RepoError, Map[AbsenceReasonId, AbsenceReason]] = ZIO.succeed(config.reasons)
    }
  }
}
