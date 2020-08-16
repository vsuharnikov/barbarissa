package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.absence.domain.{Absence, AbsenceClaimRequest, AbsenceReasonRepo, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AbsenceAppointmentService
import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import kantan.csv._
import kantan.csv.ops._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.multipart.Multipart
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.{EntityDecoder, Request, Response, Status}
import zio.clock.Clock
import zio.interop.catz._
import zio.logging._
import zio.{RIO, URIO, ZIO}

class EmployeeHttpApiRoutes[
    R <: Clock with Logging with EmployeeRepo with AbsenceRepo with AbsenceReasonRepo with AbsenceQueue with ReportService with AbsenceAppointmentService with ProcessingService](
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
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") |>> { (id: EmployeeId) =>
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
      (id: EmployeeId, api: HttpV0UpdateEmployee) =>
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
      req.decode[Multipart[HttpIO]] { m =>
        m.parts.headOption match {
          case None => ZIO.fail(ApiError.clientError("Expected at least one CSV file"))
          case Some(value) =>
            for {
              csvContent <- value.as[String](monadErrorInstance, EntityDecoder.text)
              invalid <- {
                val reader = csvContent.asCsvReader[CsvEmployee](CsvConfiguration.rfc)

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
                  .forkDaemon *> ZIO.succeed(invalid)
              }
            } yield Response[HttpIO](Status.Ok).withEntity(HttpV0BatchUpdateResponse(invalid))
        }
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

    "Add an item to the queue" **
      "queue" @@
        POST / "api" / "v0" / "queue" / "add" ^ circeJsonDecoder[HttpV0AbsenceQueueItem] |>> { (api: HttpV0AbsenceQueueItem) =>
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
        POST / "api" / "v0" / "processing" / "refreshQueue" |>> {
      ProcessingService.refreshQueue *> Ok("Done")
    }

    "Process items in the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "process" |>> {
      ProcessingService.processQueue *> Ok("Processed")
    }
  }

  private def httpEmployeeFrom(domain: Employee): HttpV0Employee = HttpV0Employee(
    id = domain.employeeId.asString,
    name = domain.name,
    localizedName = domain.localizedName,
    companyId = domain.companyId.map(_.asString),
    email = domain.email,
    position = domain.position
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
