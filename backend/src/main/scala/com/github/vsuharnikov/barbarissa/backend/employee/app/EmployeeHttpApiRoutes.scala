package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs
import com.github.vsuharnikov.barbarissa.backend.shared.app.{HttpSearchCursor, JsonSupport, ListResponse, RoutesParsers}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Inflection, ReportService, error => domainError}
import doobie.`enum`.FetchDirection.Forward
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.config.config
import zio.interop.catz._
import zio.{Has, RIO, ZIO}

class EmployeeHttpApiRoutes[
    R <: Has[EmployeeHttpApiRoutes.Config] with EmployeeRepo with AbsenceRepo with AbsenceReasonRepo with AbsenceQueue with ReportService with AbsenceAppointmentService with ProcessingService](
    inflection: Inflection)
    extends JsonSupport[RIO[R, *]] {
  type HttpIO[A] = RIO[R, A]

  private val locale        = Locale.forLanguageTag("ru")
  private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(new Locale("ru"))

  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]
    import parsers._

    "Gets an employee by id" **
      "employee" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") |>> { id: EmployeeId =>
      EmployeeRepo
        .get(id)
        .map {
          case Some(x) => Ok(httpEmployeeFrom(x))
          case None    => NotFound(s"Can't find an employee with id=$id") // TODO
        }
    }

    "Updates an employee by id" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") ^ circeJsonDecoder[HttpV0UpdateEmployee] |>> {
      (id: EmployeeId, api: HttpV0UpdateEmployee) =>
        val r = for {
          orig <- EmployeeRepo.get(id)
          orig <- orig match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
          }
          _       <- EmployeeRepo.update(draft(orig, api))
          updated <- EmployeeRepo.get(id)
          updated <- updated match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
          }
        } yield updated

        r.map(x => Ok(httpEmployeeFrom(x)))
    }

    "Gets an employee's absences" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") / "absence" +? param[Option[HttpSearchCursor]]("cursor") |>> {
      (id: EmployeeId, cursor: Option[HttpSearchCursor]) =>
        AbsenceRepo
          .getByCursor(AbsenceRepo.GetCursor(id, cursor.fold(0)(_.startAt), cursor.fold(0)(_.maxResults)))
          .map {
            case (xs, nextCursor) => Ok(ListResponse(xs.map(httpAbsenceFrom), nextCursor.map(c => HttpSearchCursor(c.startAt, c.maxResults))))
          }
    }

    "Gets an employee's absence by id" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("employeeId") / "absence" / pathVar[AbsenceId]("absenceId") |>> {
      (eid: EmployeeId, aid: AbsenceId) =>
        AbsenceRepo
          .get(aid)
          .map { x =>
            Ok(httpAbsenceFrom(x))
          }
    }

    "Generates a claim for employee's absence" **
      "absence" @@
        POST / "api" / "v0" / "employee" / pathVar[EmployeeId]("employeeId") / "absence" / pathVar[AbsenceId]("absenceId") / "claim" |>> {
      (eid: EmployeeId, aid: AbsenceId) =>
        // TODO
        val r = for {
          c        <- config[EmployeeHttpApiRoutes.Config]
          employee <- EmployeeRepo.get(eid)
          employee <- employee match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
          }
          absence       <- AbsenceRepo.get(aid)
          absenceReason <- AbsenceReasonRepo.get(absence.reason.id)
          absenceReasonSuffix <- absenceReason.needClaim match {
            case Some(AbsenceClaimType.WithoutCompensation) => ZIO.succeed("without-compensation")
            case Some(AbsenceClaimType.WithCompensation)    => ZIO.succeed("with-compensation")
            case None                                       => ZIO.fail(domainError.ClaimNotRequired)
          }
          templateFile <- {
            val companyId = employee.companyId.map(_.asString).getOrElse("unknown")
            val fileName  = s"$companyId-$absenceReasonSuffix.docx"
            val r         = c.templates.rootDir.toPath.resolve(fileName).toFile
            // TODO log library
            logger.warn(s"Can't find '$r' file")
            if (r.isFile) ZIO.succeed(r)
            else ZIO.fail(domainError.TemplateNotFound)
          }
          report <- {
            val data = absenceClaimRequestFrom(employee, absence)
            ReportService.generate(templateFile, ToArgs.toArgs(data).toMap)
          }
        } yield report

        r.fold(
          {
            case domainError.RepoRecordNotFound => NotFound("")
            case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
            case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
            case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
          },
          x => Ok(x)
        )
    }

    "Gets an appointment for employee's absence" **
      "appointment" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("employeeId") / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> {
      (eid: EmployeeId, aid: AbsenceId) =>
        val r = for {
          absence <- AbsenceRepo.get(aid)
          maybeAppointment <- {
            val searchFilter = SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.id.asString
            )
            AbsenceAppointmentService.get(searchFilter)
          }
          appointment <- maybeAppointment match {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
        } yield appointment

        r.map(x => Ok(httpAbsenceAppointmentFrom(x)))
    }

    "Places an appointment for employee's absence" **
      "appointment" @@
        PUT / "api" / "v0" / "employee" / pathVar[EmployeeId]("employeeId") / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> {
      (eid: EmployeeId, aid: AbsenceId) =>
        val r = for {
          employee <- EmployeeRepo.get(eid)
          employee <- employee match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
          }
          absence <- AbsenceRepo.get(aid)
          has <- {
            val searchFilter = SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.id.asString
            )
            AbsenceAppointmentService.has(searchFilter)
          }
          _ <- ZIO.when(!has) {
            val absenceAppointment = AbsenceAppointment(
              subject = s"Ухожу в отпуск ${employee.localizedName.get}",
              description = "",
              startDate = absence.from,
              endDate = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.id.asString
            )
            AbsenceAppointmentService.add(absenceAppointment)
          }
        } yield has

        r.map {
          case false => Ok("")
          case true  => Created("")
        }
    }

    "Add an item to the queue" **
      "queue" @@
        POST / "api" / "v0" / "queue" / "add" ^ circeJsonDecoder[AbsenceQueueItem] |>> { draft: AbsenceQueueItem =>
      AbsenceQueue.add(List(draft)).as(Ok("Added"))
    }

    "Refreshes the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "refreshQueue" |>> {
      ProcessingService.refreshQueue.as(Ok(""))
    }

    "Process items in the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "process" |>> {
      ProcessingService.process.as(Ok("Processed"))
    }
  }

  private def httpAbsenceFrom(domain: Absence): HttpV0Absence = HttpV0Absence(
    id = domain.id.asString,
    from = domain.from,
    daysQuantity = domain.daysQuantity,
    reason = domain.reason.name
  )

  private def httpEmployeeFrom(domain: Employee): HttpV0Employee = HttpV0Employee(
    id = domain.id.asString,
    name = domain.name,
    localizedName = domain.localizedName,
    companyId = domain.companyId.map(_.asString),
    email = domain.email,
    position = domain.position
  )

  private def httpAbsenceAppointmentFrom(domain: AbsenceAppointment): HttpV0AbsenceAppointment = HttpV0AbsenceAppointment(
    subject = domain.subject,
    description = domain.description,
    startDate = domain.startDate,
    endDate = domain.endDate,
    serviceMark = domain.serviceMark
  )

  private def draft(domain: Employee, api: HttpV0UpdateEmployee): Employee = domain.copy(
    localizedName = api.localizedName.some,
    companyId = api.companyId.some.map(CompanyId),
    position = api.position.some
  )

  private def absenceClaimRequestFrom(e: Employee, a: Absence): AbsenceClaimRequest = AbsenceClaimRequest(
    sinGenPosition = inflection.dativeAppointment(e.position.getOrElse("???").toLowerCase(locale)), // TODO
    sinGenFullName = inflection.dativeName(e.localizedName.getOrElse("???"), e.sex),
    sinGenFromDate = toSinGenDateStr(a.from),
    daysQuantity = a.daysQuantity,
    reportDate = toDateStr(a.from.minus(1, ChronoUnit.MONTHS))
  )

  private def toSinGenDateStr(x: LocalDate): String = s"${x.getDayOfMonth} ${x.getMonth.getDisplayName(TextStyle.FULL, locale)} ${x.getYear}"
  private def toDateStr(x: LocalDate): String       = dateFormatter.format(x)
}

object EmployeeHttpApiRoutes {
  case class TemplatesConfig(rootDir: File)
  case class Config(templates: TemplatesConfig)
}
