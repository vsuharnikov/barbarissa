package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.nio.charset.StandardCharsets

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import kantan.csv._
import kantan.csv.ops._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir._
import zio.interop.catz._
import zio.logging.Logger
import zio.macros.accessible
import zio.{ZIO, ZLayer}

@accessible
object EmployeeHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromServices[Logger[String], EmployeeRepo.Service, Service] { (logger, employeeRepo) =>
    new Service with TapirCommonEntities {
      val tag = "employee"

      val get = endpoint.get
        .in("api" / "v0" / "employee" / employeeIdPath)
        .out(jsonBody[HttpV0Employee])
        .errorOut(errorOut)
        .tag(tag)
        .description("Gets an employee by id")

      val getRoute = get.toRoutes { employeeId =>
        employeeRepo.unsafeGet(employeeId).map(httpEmployeeFrom)
      }

      val update = endpoint.patch
        .in("api" / "v0" / "employee" / employeeIdPath)
        .in(jsonBody[HttpV0UpdateEmployee])
        .out(jsonBody[HttpV0Employee])
        .errorOut(errorOut)
        .tag(tag)
        .description("Updates an employee by id")

      val updateRoute = update.toRoutes {
        case (employeeId, api) =>
          for {
            orig    <- employeeRepo.unsafeGet(employeeId)
            _       <- employeeRepo.update(draft(orig, api))
            updated <- employeeRepo.unsafeGet(employeeId)
          } yield httpEmployeeFrom(updated)
      }

      val batchUpdate = endpoint.patch
        .in("api" / "vo" / "employee")
        .in(multipartBody)
        .out(jsonBody[HttpV0BatchUpdateResponse])
        .errorOut(errorOut)
        .tag(tag)
        .description("Batch update for employees")

      val batchUpdateRoute = batchUpdate.toRoutes { parts =>
        parts.headOption match {
          case None => ZIO.fail(ApiError.clientError("Expected at least one CSV file"))
          case Some(value) =>
            for {
              invalid <- {
                val csvContent = new String(value.body, StandardCharsets.UTF_8)
                val reader     = csvContent.asCsvReader[CsvEmployee](CsvConfiguration.rfc)

                val (invalid, valid) = reader.zipWithIndex.foldLeft((List.empty[String], List.empty[CsvEmployee])) {
                  case ((invalid, valid), (Left(x), i))  => (s"$i: ${x.getMessage}" :: invalid, valid)
                  case ((invalid, valid), (Right(x), _)) => (invalid, x :: valid)
                }

                ZIO
                  .foreach_(valid) { csv =>
                    val update = for {
                      orig <- employeeRepo.search(csv.email)
                      _ <- ZIO.foreach(orig) { orig =>
                        employeeRepo
                          .update(
                            orig.copy(
                              localizedName = csv.name.some,
                              companyId = csv.companyId.some,
                              position = csv.position.some,
                              sex = csv.sex.some
                            ))
                      }
                    } yield ()
                    update.ignore
                  }
                  .tap(_ => ZIO.effect(logger.info("Done the batch update")))
                  .forkDaemon *> ZIO.succeed(invalid)
              }
            } yield HttpV0BatchUpdateResponse(invalid)
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

      implicit val csvEmployeeRowDecoder: RowDecoder[CsvEmployee] = RowDecoder.ordered {
        (name: String, position: String, email: String, rawSex: String, rawCompanyId: String) =>
          val sex = rawSex match {
            case "МУЖ" => Sex.Male
            case "ЖЕН" => Sex.Female
            case x     => throw new RuntimeException(s"Can't parse sex: $x")
          }
          CsvEmployee(name, position, email, sex, CompanyId(rawCompanyId))
      }

      override val openApiDoc   = List(get, update, batchUpdate).toOpenAPI("", "")
      override val http4sRoutes = List(getRoute, updateRoute, batchUpdateRoute)
    }
  }

  private case class CsvEmployee(name: String, position: String, email: String, sex: Sex, companyId: CompanyId)
}
