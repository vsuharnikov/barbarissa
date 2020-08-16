package com.github.vsuharnikov.barbarissa.backend.employee.app

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.app.EmployeeHttpApiRoutes.CsvEmployee
import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
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
import zio.{RIO, ZIO}

class EmployeeHttpApiRoutes[R <: Clock with Logging with EmployeeRepo] extends ApiRoutes[R] with JsonEntitiesEncoding[RIO[R, *]] {
  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  override val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]()
    import parsers._

    "Gets an employee by id" **
      "employee" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") |>> { (id: EmployeeId) =>
      EmployeeRepo.unsafeGet(id).flatMap(x => Ok(httpEmployeeFrom(x)))
    }

    "Updates an employee by id" **
      "employee" @@
        PATCH / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") ^ circeJsonDecoder[HttpV0UpdateEmployee] |>> {
      (id: EmployeeId, api: HttpV0UpdateEmployee) =>
        for {
          orig    <- EmployeeRepo.unsafeGet(id)
          _       <- EmployeeRepo.update(draft(orig, api))
          updated <- EmployeeRepo.unsafeGet(id)
          r       <- Ok(httpEmployeeFrom(updated))
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
}

private object EmployeeHttpApiRoutes {
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
