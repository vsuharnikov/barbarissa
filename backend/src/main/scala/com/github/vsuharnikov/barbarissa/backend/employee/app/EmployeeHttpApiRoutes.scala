package com.github.vsuharnikov.barbarissa.backend.employee.app

import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import kantan.csv._
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir._
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZLayer}

@accessible
object EmployeeHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromService[EmployeeRepo.Service, Service] { employeeRepo =>
    new Service with TapirCommonEntities with JsonEntitiesEncoding[Task] {
      // errorOut
      val getEmployee = endpoint.get
        .in("api" / "v0" / "employee" / employeeIdPath)
        .out(jsonBody[HttpV0Employee])
        .errorOut(errorOut)

      val getEmployeeRoute: HttpRoutes[Task] = getEmployee.toRoutes { employeeId =>
        employeeRepo.unsafeGet(employeeId).map(httpEmployeeFrom)
      }

      val updateEmployee = endpoint.patch
        .in("api" / "v0" / "employee" / employeeIdPath)
        .in(jsonBody[HttpV0UpdateEmployee])
        .out(jsonBody[HttpV0Employee])

      override val http4sRoutes = getEmployeeRoute
      override val openApiDocs  = List(getEmployee, updateEmployee).toOpenAPI("", "")

      private def httpEmployeeFrom(domain: Employee): HttpV0Employee = HttpV0Employee(
        id = domain.employeeId.asString,
        name = domain.name,
        localizedName = domain.localizedName,
        companyId = domain.companyId.map(_.asString),
        email = domain.email,
        position = domain.position
      )
    }
  }

  private case class CsvEmployee(name: String, position: String, email: String, sex: Sex, companyId: CompanyId)
  private object CsvEmployee {
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
