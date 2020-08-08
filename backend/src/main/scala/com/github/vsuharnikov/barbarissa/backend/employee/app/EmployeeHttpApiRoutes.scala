package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee._
import com.github.vsuharnikov.barbarissa.backend.employee.app.entities.{HttpV0Absence, HttpV0AbsenceAppointment, HttpV0AbsenceQueueItem, HttpV0BatchUpdateResponse, HttpV0Employee, HttpV0UpdateEmployee, JsonEntitiesEncoding}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{DomainError, Inflection, ReportService, Sex}
import kantan.csv._
import kantan.csv.ops._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.{EntityDecoder, Request, Response, Status}
import zio.interop.catz._
import zio.logging._
import zio.{Has, RIO, URIO, ZIO}

class EmployeeHttpApiRoutes[
    R <: Has[EmployeeHttpApiRoutes.Config] with Logging with EmployeeRepo with AbsenceRepo with AbsenceReasonRepo with AbsenceQueue with ReportService with AbsenceAppointmentService with ProcessingService](
    inflection: Inflection)
    extends JsonEntitiesEncoding[RIO[R, *]] {
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
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") |>> { (req: Request[HttpIO], id: EmployeeId) =>
      EmployeeRepo
        .get(id)
        .flatMap {
          case Some(x) => Ok(httpEmployeeFrom(x))
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Employee", id.asString)))
        }
    }

    "Updates an employee by id" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") ^ circeJsonDecoder[HttpV0UpdateEmployee] |>> {
      (req: Request[HttpIO], id: EmployeeId, api: HttpV0UpdateEmployee) =>
        for {
          orig <- EmployeeRepo.get(id)
          orig <- orig match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Employee", id.asString)))
          }
          _       <- EmployeeRepo.update(draft(orig, api))
          updated <- EmployeeRepo.get(id)
          updated <- updated match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Employee", id.asString)))
          }
          r <- Ok(httpEmployeeFrom(updated))
        } yield r
    }

    "Batch update for employees" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" |>> { (req: Request[HttpIO]) =>
      req.decodeWith(EntityDecoder.text[HttpIO], strict = true) { x =>
        val reader = x.asCsvReader[CsvEmployee](CsvConfiguration.rfc)

        val (invalid, valid) = reader.zipWithIndex.foldLeft((List.empty[String], List.empty[CsvEmployee])) {
          case ((invalid, valid), (Left(x), i))  => (s"$i: ${x.getMessage}" :: invalid, valid)
          case ((invalid, valid), (Right(x), _)) => (invalid, x :: valid)
        }

        ZIO
          .foreach(valid) { csv =>
            for {
              orig <- EmployeeRepo.search(csv.email)
              _ <- ZIO.foreach(orig) { orig =>
                EmployeeRepo.update(
                  orig.copy(
                    localizedName = csv.name.some,
                    companyId = csv.companyId.some,
                    position = csv.position.some,
                    sex = csv.sex.some
                  ))
              }
            } yield ()
          }
          .forkDaemon *>
          ZIO.succeed(Response[HttpIO](Status.Ok).withEntity(HttpV0BatchUpdateResponse(invalid)))
      }
    }

    "Gets an employee's absences" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") / "absence" +? param[Option[HttpSearchCursor]]("cursor") |>> {
      (req: Request[HttpIO], id: EmployeeId, cursor: Option[HttpSearchCursor]) =>
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

    "Gets an absence by id" **
      "absence" @@
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") |>> { (req: Request[HttpIO], id: AbsenceId) =>
      for {
        absence <- AbsenceRepo.get(id).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Absence", id.asString)))
        }
        absenceReason <- AbsenceReasonRepo.get(absence.reasonId).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Absence", id.asString)))
        }
        r <- Ok(httpAbsenceFrom(absence, absenceReason))
      } yield r
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
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (req: Request[HttpIO], id: AbsenceId) =>
      for {
        absence <- AbsenceRepo.get(id).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Absence", id.asString)))
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
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", id.asString)))
        }
        r <- Ok(httpAbsenceAppointmentFrom(appointment))
      } yield r
    }

    "Places an appointment for employee's absence" **
      "appointment" @@
        PUT / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") / "appointment" |>> { (req: Request[HttpIO], id: AbsenceId) =>
      for {
        absence <- AbsenceRepo.get(id).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", id.asString)))
        }
        employee <- EmployeeRepo.get(absence.employeeId).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("AbsenceAppointment", id.asString)))
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

    "Add an item to the queue" **
      "queue" @@
        POST / "api" / "v0" / "queue" / "add" ^ circeJsonDecoder[HttpV0AbsenceQueueItem] |>> { (req: Request[HttpIO], api: HttpV0AbsenceQueueItem) =>
      val draft = AbsenceQueueItem(
        absenceId = AbsenceId(api.absenceId),
        done = api.done,
        claimSent = api.done,
        appointmentCreated = api.appointmentCreated,
        retries = api.retries
      )
      AbsenceQueue.add(List(draft)) *> Ok("Added")
    }

    "Refreshes the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "refreshQueue" |>> { (req: Request[HttpIO]) =>
      ProcessingService.refreshQueue *> Ok("Done")
    }

    "Process items in the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "process" |>> { (req: Request[HttpIO]) =>
      ProcessingService.process *> Ok("Processed")
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

  case class CsvEmployee(name: String, position: String, email: String, sex: Sex, companyId: CompanyId)
  object CsvEmployee {
    implicit val csvEmployeeRowDecoder: RowDecoder[CsvEmployee] = RowDecoder.ordered {
      (name: String, position: String, email: String, rawSex: String, rawCompanyId: String) =>
        val sex = rawSex match {
          case "МУЖ" => Sex.Male
          case "ЖЕН" => Sex.Female
          case x     => throw new RuntimeException(s"Can't parse sex: $x")
        }
        CsvEmployee(name, position, email, sex, CompanyId(rawCompanyId))
    }
  }
}

object EmployeeHttpApiRoutes {
  case class TemplatesConfig(rootDir: File)
  case class Config(templates: TemplatesConfig)
}
