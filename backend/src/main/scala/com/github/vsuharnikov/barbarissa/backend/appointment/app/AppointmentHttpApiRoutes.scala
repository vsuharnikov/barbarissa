package com.github.vsuharnikov.barbarissa.backend.appointment.app

import cats.implicits._
import com.github.vsuharnikov.barbarissa.backend.HttpApiConfig
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.appointment.app.entities.{HttpV0AbsenceAppointment, HttpV0PutAbsenceResponse}
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{Appointment, AppointmentService}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.EmployeeRepo
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.ztapir._
import zio.interop.catz._
import zio.macros.accessible
import zio.{ZIO, ZLayer}

@accessible
object AppointmentHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromServices[HttpApiConfig, EmployeeRepo.Service, AbsenceRepo.Service, AppointmentService.Service, Service] {
    (config, employeeRepo, absenceRepo, appService) =>
      new Service with TapirCommonEntities {
        val tag = "appointment"

        val securedEndpoint = TapirSecuredEndpoint(config.apiKeyHashBytes)

        val get = securedEndpoint.get
          .in("api" / "v0" / "absence" / absenceIdPath / "appointment")
          .out(jsonBody[HttpV0AbsenceAppointment])
          .tag(tag)
          .description("Gets an appointment for employee's absence")
          .serverLogicRecoverErrors {
            case (_, absenceId) =>
              for {
                absence <- absenceRepo.unsafeGet(absenceId)
                maybeAppointment <- {
                  val searchFilter = SearchFilter(
                    start = absence.from,
                    end = absence.from.plusDays(absence.daysQuantity),
                    serviceMark = absence.absenceId.asString
                  )
                  appService.get(searchFilter)
                }
                appointment <- maybeAppointment match {
                  case Some(x) => ZIO.succeed(x)
                  case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", absenceId.asString)))
                }
              } yield httpAbsenceAppointmentFrom(appointment)
          }

        val put = securedEndpoint.put
          .in("api" / "v0" / "absence" / absenceIdPath / "appointment")
          .out(oneOf[HttpV0PutAbsenceResponse](
            statusMapping[HttpV0PutAbsenceResponse.New](StatusCode.Ok, jsonBody[HttpV0PutAbsenceResponse.New]),
            statusMapping[HttpV0PutAbsenceResponse.Created.type](StatusCode.Created, jsonBody[HttpV0PutAbsenceResponse.Created.type])
          ))
          .tag(tag)
          .description("Places an appointment for employee's absence")
          .serverLogicRecoverErrors {
            case (_, absenceId) =>
              for {
                absence  <- absenceRepo.unsafeGet(absenceId)
                employee <- employeeRepo.unsafeGet(absence.employeeId)
                has <- {
                  val searchFilter = SearchFilter(
                    start = absence.from,
                    end = absence.from.plusDays(absence.daysQuantity),
                    serviceMark = absence.absenceId.asString
                  )
                  appService.has(searchFilter)
                }
                added <- if (has) ZIO.succeed(none[HttpV0AbsenceAppointment])
                else {
                  val absenceAppointment = Appointment(
                    subject = s"Ухожу в отпуск ${employee.localizedName.get}",
                    description = "",
                    startDate = absence.from,
                    endDate = absence.from.plusDays(absence.daysQuantity),
                    serviceMark = absence.absenceId.asString
                  )
                  appService.add(absenceAppointment).as(httpAbsenceAppointmentFrom(absenceAppointment).some)
                }
              } yield added.fold[HttpV0PutAbsenceResponse](HttpV0PutAbsenceResponse.Created)(HttpV0PutAbsenceResponse.New(_))
          }

        private def httpAbsenceAppointmentFrom(domain: Appointment): HttpV0AbsenceAppointment = HttpV0AbsenceAppointment(
          subject = domain.subject,
          description = domain.description,
          startDate = domain.startDate,
          endDate = domain.endDate,
          serviceMark = domain.serviceMark
        )

        override val openApiDoc   = List(get, put).toOpenAPI("", "")
        override val http4sRoutes = List(get.toRoutes, put.toRoutes)
      }
  }
}
