package com.github.vsuharnikov.barbarissa.backend.appointment.app

import com.github.vsuharnikov.barbarissa.backend.absence.app.entities.HttpV0AbsenceAppointment
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{Appointment, AppointmentService}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.EmployeeRepo
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZIO, ZLayer}

@accessible
object AppointmentHttpApiRoutes extends Serializable {
  trait Service extends HasRhoRoutes

  val live = ZLayer.fromServices[EmployeeRepo.Service, AbsenceRepo.Service, AppointmentService.Service, Service] {
    (employeeRepo, absenceRepo, appService) =>
      new Service with JsonEntitiesEncoding[Task] {
        private val swaggerSyntax = new SwaggerSupport[Task] {}
        import swaggerSyntax._

        override val rhoRoutes: RhoRoutes[Task] = new RhoRoutes[Task] {
          val parsers = new RoutesParsers[Task]()
          import parsers._

          "Gets an appointment for employee's absence" **
            "appointment" @@
              GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (id: AbsenceId) =>
            for {
              absence <- absenceRepo.unsafeGet(id)
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
                case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", id.asString)))
              }
              r <- Ok(httpAbsenceAppointmentFrom(appointment))
            } yield r
          }

          "Places an appointment for employee's absence" **
            "appointment" @@
              PUT / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (id: AbsenceId) =>
            for {
              absence  <- absenceRepo.unsafeGet(id)
              employee <- employeeRepo.unsafeGet(absence.employeeId)
              has <- {
                val searchFilter = SearchFilter(
                  start = absence.from,
                  end = absence.from.plusDays(absence.daysQuantity),
                  serviceMark = absence.absenceId.asString
                )
                appService.has(searchFilter)
              }
              _ <- ZIO.when(!has) {
                val absenceAppointment = Appointment(
                  subject = s"Ухожу в отпуск ${employee.localizedName.get}",
                  description = "",
                  startDate = absence.from,
                  endDate = absence.from.plusDays(absence.daysQuantity),
                  serviceMark = absence.absenceId.asString
                )
                appService.add(absenceAppointment)
              }
              r <- if (has) Ok("") else Created("")
            } yield r
          }
        }

        private def httpAbsenceAppointmentFrom(domain: Appointment): HttpV0AbsenceAppointment = HttpV0AbsenceAppointment(
          subject = domain.subject,
          description = domain.description,
          startDate = domain.startDate,
          endDate = domain.endDate,
          serviceMark = domain.serviceMark
        )
      }
  }
}
