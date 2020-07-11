package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.io.File
import java.time.temporal.ChronoUnit

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Absence, AbsenceClaimRequest, AbsenceRepo, Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{ReportService, error => domainError}
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.RIO
import zio.interop.catz._

class EmployeeHttpApiRoutes[R <: EmployeeRepo with AbsenceRepo with ReportService] extends JsonSupport[RIO[R, *]] {
  type HttpIO[A] = RIO[R, A]

  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    "Gets an employee by id" **
      "employee" @@
        GET / "api" / "v0" / "employee" / pathVar[String]("id") |>> { id: String =>
      EmployeeRepo
        .get(EmployeeId(id))
        .fold(
          {
            case domainError.RepoRecordNotFound => NotFound(s"Can't find an employee with id=$id") // TODO
            case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
            case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
            case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
          },
          x => Ok(httpEmployeeFrom(x))
        )
    }

    "Updates an employee by id" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" / pathVar[String]("id") ^ circeJsonDecoder[HttpV0UpdateEmployee] |>> {
      (id: String, api: HttpV0UpdateEmployee) =>
        val r = for {
          orig    <- EmployeeRepo.get(EmployeeId(id))
          _       <- EmployeeRepo.update(draft(orig, api))
          updated <- EmployeeRepo.get(EmployeeId(id))
        } yield updated

        r.fold(
          {
            case domainError.RepoRecordNotFound => NotFound(s"Can't find an employee with id=$id") // TODO
            case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
            case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
            case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
          },
          x => Ok(httpEmployeeFrom(x))
        )
    }

    "Gets an employee's absences" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[String]("id") / "absence" |>> { id: String =>
      AbsenceRepo
        .get(EmployeeId(id))
        .fold(
          {
            case domainError.RepoRecordNotFound => NotFound(s"Can't find an employee with id=$id") // TODO
            case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
            case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
            case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
          },
          x => Ok(x.map(httpAbsenceFrom))
        )
    }

    "Gets an employee's absence by id" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[String]("employeeId") / "absence" / pathVar[String]("absenceId") |>> {
      (employeeId: String, absenceId: String) =>
        AbsenceRepo
          .get(EmployeeId(employeeId), AbsenceId(absenceId))
          .fold(
            {
              case domainError.RepoRecordNotFound => NotFound("")
              case domainError.RepoRecordBroken   => BadGateway("The employee record is broken, try to update")
              case domainError.RepoNotAvailable   => BadGateway("Can't get the employee, because the service is not available")
              case domainError.RepoUnknown        => InternalServerError("Probably a bug. Ask the administrator")
            },
            x => Ok(httpAbsenceFrom(x))
          )
    }

    "Generates a claim for employee's absence" **
      "absence" @@
        POST / "api" / "v0" / "employee" / pathVar[String]("employeeId") / "absence" / pathVar[String]("absenceId") / "report" |>> {
      (employeeId: String, absenceId: String) =>
        val eid          = EmployeeId(employeeId)
        val aid          = AbsenceId(absenceId)
        val templateFile = new File("/absence-claim.docx")
        val r = for {
          employee <- EmployeeRepo.get(eid)
          absence  <- AbsenceRepo.get(eid, aid)
          report <- {
            val data = AbsenceClaimRequest(
              singularGenitivePosition = employee.position.getOrElse("???"),
              singularGenitiveFullName = employee.localizedName.getOrElse("???"),
              from = absence.from,
              daysQuantity = absence.daysQuantity,
              reportDate = absence.from.minus(1, ChronoUnit.MONTHS)
            )
            ReportService.generate(templateFile, implicitly[ToArgs[AbsenceClaimRequest]].toArgs(data).toMap)
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
  }

  private def httpAbsenceFrom(domain: Absence): HttpV0Absence = HttpV0Absence(
    id = domain.id.asString,
    from = domain.from,
    daysQuantity = domain.daysQuantity,
    reason = domain.reason
  )

  private def httpEmployeeFrom(domain: Employee): HttpV0Employee = HttpV0Employee(
    id = domain.id.asString,
    name = domain.name,
    localizedName = domain.localizedName,
    email = domain.email,
    position = domain.position
  )

  private def draft(domain: Employee, api: HttpV0UpdateEmployee): Employee = domain.copy(
    localizedName = api.localizedName.some,
    position = api.position.some
  )
}
