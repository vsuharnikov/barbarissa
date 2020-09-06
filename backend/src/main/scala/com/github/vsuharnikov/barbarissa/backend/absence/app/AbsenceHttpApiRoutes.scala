package com.github.vsuharnikov.barbarissa.backend.absence.app

import cats.implicits._
import com.github.vsuharnikov.barbarissa.backend.HttpApiConfig
import com.github.vsuharnikov.barbarissa.backend.absence.app.entities.HttpV0Absence
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir._
import zio.interop.catz._
import zio.macros.accessible
import zio.{ZIO, ZLayer}

@accessible
object AbsenceHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromServices[HttpApiConfig, AbsenceRepo.Service, AbsenceReasonRepo.Service, Service] { (config, absenceRepo, absenceReasonRepo) =>
    new Service with TapirCommonEntities {
      val tag = "absence"

      val securedEndpoint = TapirSecuredEndpoint(config.apiKeyHashBytes)

      val getAllByEmployee = securedEndpoint.get
        .in("api" / "v0" / "employee" / employeeIdPath / "absence" / searchCursorQuery)
        .out(jsonBody[ListResponse[HttpV0Absence]])
        .tag(tag)
        .description("Gets an employee's absences")
        .serverLogicRecoverErrors {
          case (_, (employeeId, cursor)) =>
            for {
              absences       <- absenceRepo.getByCursor(AbsenceRepo.GetCursor(employeeId, cursor.fold(0)(_.startAt), cursor.fold(10)(_.maxResults)))
              absenceReasons <- absenceReasonRepo.all
              body <- {
                val (as, nextCursor) = absences
                val r = as.foldLeft(List.empty[HttpV0Absence].asRight[List[String]]) {
                  case (r, a) =>
                    absenceReasons.get(a.reasonId) match {
                      case Some(ar) => r.map(httpAbsenceFrom(a, ar) :: _)
                      case None     => r.leftMap(a.reasonId.asString :: _)
                    }
                }
                r match {
                  case Left(e) => ZIO.fail(ApiError.from(DomainError.ConfigurationError(s"Unknown absence reasons: ${e.mkString(", ")}")))
                  case Right(r) =>
                    ZIO.succeed(
                      ListResponse(
                        r,
                        nextCursor.map(c => HttpSearchCursor(c.startAt, c.maxResults))
                      ))
                }
              }
            } yield body
        }

      val get = securedEndpoint.get
        .in("api" / "v0" / "absence" / absenceIdPath)
        .out(jsonBody[HttpV0Absence])
        .tag(tag)
        .description("Gets an absence by id")
        .serverLogicRecoverErrors {
          case (_, absenceId) =>
            for {
              absence       <- absenceRepo.unsafeGet(absenceId)
              absenceReason <- absenceReasonRepo.unsafeGet(absence.reasonId)
            } yield httpAbsenceFrom(absence, absenceReason)
        }

      private def httpAbsenceFrom(domain: Absence, absenceReason: AbsenceReason): HttpV0Absence = HttpV0Absence(
        id = domain.absenceId.asString,
        from = domain.from,
        daysQuantity = domain.daysQuantity,
        reason = absenceReason.name
      )

      override val openApiDoc   = List(getAllByEmployee, get).toOpenAPI("", "")
      override val http4sRoutes = List(getAllByEmployee.toRoutes, get.toRoutes)
    }
  }
}
