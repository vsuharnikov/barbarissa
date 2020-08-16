package com.github.vsuharnikov.barbarissa.backend.appointment.app

import com.github.vsuharnikov.barbarissa.backend.absence.app.entities.HttpV0AbsenceAppointment
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{AbsenceAppointment, AbsenceAppointmentService}
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.{RIO, ZIO}

class AppointmentHttpApiRoutes[R <: EmployeeRepo with AbsenceRepo with AbsenceAppointmentService]
    extends ApiRoutes[R]
    with JsonEntitiesEncoding[RIO[R, *]] {
  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  override val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]()
    import parsers._

    "Gets an appointment for employee's absence" **
      "appointment" @@
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (id: AbsenceId) =>
      for {
        absence <- AbsenceRepo.unsafeGet(id)
        maybeAppointment <- {
          val searchFilter = SearchFilter(
            start = absence.from,
            end = absence.from.plusDays(absence.daysQuantity),
            serviceMark = absence.absenceId.asString
          )
          AbsenceAppointmentService.get(searchFilter)
        }
        appointment <- maybeAppointment match {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", id.asString)))
        }
        r <- Ok(httpAbsenceAppointmentFrom(appointment))
      } yield r
    }

    "Places an appointment for employee's absence" **
      "appointment" @@
        PUT / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (id: AbsenceId) =>
      for {
        absence  <- AbsenceRepo.unsafeGet(id)
        employee <- EmployeeRepo.unsafeGet(absence.employeeId)
        has <- {
          val searchFilter = SearchFilter(
            start = absence.from,
            end = absence.from.plusDays(absence.daysQuantity),
            serviceMark = absence.absenceId.asString
          )
          AbsenceAppointmentService.has(searchFilter)
        }
        _ <- ZIO.when(!has) {
          val absenceAppointment = AbsenceAppointment(
            subject = s"Ухожу в отпуск ${employee.localizedName.get}",
            description = "",
            startDate = absence.from,
            endDate = absence.from.plusDays(absence.daysQuantity),
            serviceMark = absence.absenceId.asString
          )
          AbsenceAppointmentService.add(absenceAppointment)
        }
        r <- if (has) Ok("") else Created("")
      } yield r
    }
  }

  private def httpAbsenceAppointmentFrom(domain: AbsenceAppointment): HttpV0AbsenceAppointment = HttpV0AbsenceAppointment(
    subject = domain.subject,
    description = domain.description,
    startDate = domain.startDate,
    endDate = domain.endDate,
    serviceMark = domain.serviceMark
  )
}
