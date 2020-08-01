package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import cats.syntax.applicative._
import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee._
import com.github.vsuharnikov.barbarissa.backend.employee.app.EmployeeHttpApiRoutes.FailFast
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.shared.app.{HttpSearchCursor, JsonSupport, ListResponse, RoutesParsers}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Inflection, ReportService, error => domainError}
import org.http4s.Status.InternalServerError
import org.http4s.{Response, Status}
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.Result.BaseResult
import org.http4s.rho.{Result, RhoRoutes}
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.{Has, RIO, URIO, ZIO}

class EmployeeHttpApiRoutes[
    R <: Has[EmployeeHttpApiRoutes.Config] with EmployeeRepo with AbsenceRepo with AbsenceReasonRepo with AbsenceQueue with ReportService with AbsenceAppointmentService with ProcessingService](
    inflection: Inflection)
    extends JsonSupport[RIO[R, *]] {
  type HttpIO[A]   = RIO[R, A]
  type HttpURIO[A] = URIO[R, A]

  private val locale        = Locale.forLanguageTag("ru")
  private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(new Locale("ru"))

  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]()
    import parsers._

    "Gets an employee by id" **
      "employee" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") |>> { id: EmployeeId =>
      handleErrors {
        EmployeeRepo
          .get(id)
          .flatMap {
            case Some(x) => Ok(httpEmployeeFrom(x))
            case None    => NotFound(s"Can't find an employee with id=$id") // TODO
          }
      }
    }

    "Updates an employee by id" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") ^ circeJsonDecoder[HttpV0UpdateEmployee] |>> {
      (id: EmployeeId, api: HttpV0UpdateEmployee) =>
        handleErrors {
          for {
            orig <- EmployeeRepo.get(id)
            orig <- orig match {
              case Some(x) => ZIO.succeed(x)
              case None    => ZIO.fail(new FailFast(Status.NotFound, s"Employee '$id' not found"))
            }
            _       <- EmployeeRepo.update(draft(orig, api))
            updated <- EmployeeRepo.get(id)
            updated <- updated match {
              case Some(x) => ZIO.succeed(x)
              case None    => ZIO.fail(new FailFast(Status.NotFound, s"Employee '$id' not found after update"))
            }
            r <- Ok(httpEmployeeFrom(updated))
          } yield r
        }
    }

    "Gets an employee's absences" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") / "absence" +? param[Option[HttpSearchCursor]]("cursor") |>> {
      (id: EmployeeId, cursor: Option[HttpSearchCursor]) =>
        handleErrors {
          for {
            absences       <- AbsenceRepo.getByCursor(AbsenceRepo.GetCursor(id, cursor.fold(0)(_.startAt), cursor.fold(0)(_.maxResults)))
            absenceReasons <- AbsenceReasonRepo.all
            r <- {
              val (as, nextCursor) = absences
              val ps               = as.map(a => (a, absenceReasons(a.reasonId))) // TODO
              Ok(
                ListResponse(
                  ps.map(Function.tupled(httpAbsenceFrom)),
                  nextCursor.map(c => HttpSearchCursor(c.startAt, c.maxResults))
                ))
            }
          } yield r
        }
    }

    "Gets an absence by id" **
      "absence" @@
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") |>> { (aid: AbsenceId) =>
      handleErrors {
        for {
          absence <- AbsenceRepo.get(aid).flatMap {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
          absenceReason <- AbsenceReasonRepo.get(absence.reasonId).flatMap {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
          r <- Ok(httpAbsenceFrom(absence, absenceReason))
        } yield r
      }
    }

//    "Generates a claim for employee's absence" **
//      "absence" @@
//        POST / "api" / "v0" / "employee" / pathVar[EmployeeId]("employeeId") / "absence" / pathVar[AbsenceId]("absenceId") / "claim" |>> {
//      (eid: EmployeeId, aid: AbsenceId) =>
//        // TODO
//        val r = for {
//          c        <- config[EmployeeHttpApiRoutes.Config]
//          employee <- EmployeeRepo.get(eid)
//          employee <- employee match {
//            case Some(x) => ZIO.succeed(x)
//            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
//          }
//          absence       <- AbsenceRepo.get(aid)
//          absenceReason <- AbsenceReasonRepo.get(absence.reasonId)
//          absenceReasonSuffix <- absenceReason.needClaim match {
//            case Some(AbsenceClaimType.WithoutCompensation) => ZIO.succeed("without-compensation")
//            case Some(AbsenceClaimType.WithCompensation)    => ZIO.succeed("with-compensation")
//            case None                                       => ZIO.fail(domainError.ClaimNotRequired)
//          }
//          templateFile <- {
//            val companyId = employee.companyId.map(_.asString).getOrElse("unknown")
//            val fileName  = s"$companyId-$absenceReasonSuffix.docx"
//            val r         = c.templates.rootDir.toPath.resolve(fileName).toFile
//            // TODO log library
//            logger.warn(s"Can't find '$r' file")
//            if (r.isFile) ZIO.succeed(r)
//            else ZIO.fail(domainError.TemplateNotFound)
//          }
//          report <- {
//            val data = absenceClaimRequestFrom(employee, absence)
//            ReportService.generate(templateFile, ToArgs.toArgs(data).toMap)
//          }
//        } yield report
//
//        r.fold(
//          {
//            case domainError.RepoRecordNotFound => NotFound("")
//            case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
//            case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
//            case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
//          },
//          x => Ok(x)
//        )
//    }

    "Gets an appointment for employee's absence" **
      "appointment" @@
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (aid: AbsenceId) =>
      handleErrors {
        for {
          absence <- AbsenceRepo.get(aid).flatMap {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
          maybeAppointment <- {
            val searchFilter = SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.absenceId.asString
            )
            AbsenceAppointmentService.get(searchFilter)
          }
          appointment <- maybeAppointment match {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
          r <- Ok(httpAbsenceAppointmentFrom(appointment))
        } yield r
      }
    }

    "Places an appointment for employee's absence" **
      "appointment" @@
        PUT / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (aid: AbsenceId) =>
      handleErrors {
        for {
          absence <- AbsenceRepo.get(aid).flatMap {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
          employee <- EmployeeRepo.get(absence.employeeId).flatMap {
            case None    => ZIO.fail(ForwardError(domainError.RepoRecordNotFound))
            case Some(x) => ZIO.succeed(x)
          }
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

    "Add an item to the queue" **
      "queue" @@
        POST / "api" / "v0" / "queue" / "add" ^ circeJsonDecoder[AbsenceQueueItem] |>> { draft: AbsenceQueueItem =>
      handleErrors {
        AbsenceQueue.add(List(draft)) *> Ok("Added")
      }
    }

    "Refreshes the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "refreshQueue" |>> {
      handleErrors {
        ProcessingService.refreshQueue *> Ok("")
      }
    }

    "Process items in the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "process" |>> {
      handleErrors {
        ProcessingService.process *> Ok("Processed")
      }
    }
  }

  private def httpAbsenceFrom(domain: Absence, absenceReason: AbsenceReason): HttpV0Absence = HttpV0Absence(
    id = domain.absenceId.asString,
    from = domain.from,
    daysQuantity = domain.daysQuantity,
    reason = absenceReason.name
  )

  private def httpEmployeeFrom(domain: Employee): HttpV0Employee = HttpV0Employee(
    id = domain.employeeId.asString,
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
    companyId = api.companyId.some.map(CompanyId(_)),
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

  // TODO https://typelevel.org/blog/2018/08/25/http4s-error-handling-mtl.html
  private def handleErrors(r: HttpIO[Result.BaseResult[HttpIO]]): ZIO[R, Nothing, BaseResult[HttpIO]] = r.foldM(
    {
      case x: FailFast => Result(Response[HttpIO](x.status).withEntity(x.message)).pure[HttpURIO]
      case x           => Result(Response[HttpIO](InternalServerError).withEntity(Option(x.getMessage).getOrElse(x.getClass.getName))).pure[HttpURIO]
    },
    x => x.pure[HttpURIO]
  )
}

object EmployeeHttpApiRoutes {
  case class TemplatesConfig(rootDir: File)
  case class Config(templates: TemplatesConfig)

  class FailFast(val status: Status, val message: String) extends RuntimeException(message, null, true, false)
  object FailFast {
    def apply(status: Status, message: String): FailFast = new FailFast(status, message)
  }
}
