package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.domain.{AbsenceReason, AbsenceReasonRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceReasonId
import zio.config.magnolia.DeriveConfigDescriptor.Descriptor
import zio.{Task, ZIO, ZLayer}

object ConfigurableAbsenceReasonRepo {
  case class Config(xs: List[AbsenceReason]) {
    val reasons = xs.groupBy(_.id).view.mapValues(_.head).toMap
  }

  object Config {
    implicit val configDescriptor: Descriptor[Config] = Descriptor[List[AbsenceReason]].xmap(Config(_), _.xs)
  }

  val live = ZLayer.fromService[Config, AbsenceReasonRepo.Service] { config =>
    new AbsenceReasonRepo.Service {
      override def get(by: AbsenceReasonId): Task[Option[AbsenceReason]] = ZIO.succeed(config.reasons.get(by))
      override def all: Task[Map[AbsenceReasonId, AbsenceReason]]        = ZIO.succeed(config.reasons)
    }
  }
}
