package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.io.File

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.HttpApiConfig
import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import kantan.csv._
import kantan.csv.ops._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.model.Part
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

  val live = ZLayer.fromServices[HttpApiConfig, Logger[String], EmployeeRepo.Service, Service] { (config, logger, employeeRepo) =>
    new Service with TapirCommonEntities {
      val tag = "employee"

      val securedEndpoint = TapirSecuredEndpoint(config.apiKeyHashBytes)

      val get = securedEndpoint.get
        .in("api" / "v0" / "employee" / employeeIdPath)
        .out(jsonBody[HttpV0Employee])
        .tag(tag)
        .description("Gets an employee by id")
        .serverLogicRecoverErrors {
          case (_, employeeId) =>
            employeeRepo.unsafeGet(employeeId).map(httpEmployeeFrom)
        }

      val update = securedEndpoint.patch
        .in("api" / "v0" / "employee" / employeeIdPath)
        .in(jsonBody[HttpV0UpdateEmployee])
        .out(jsonBody[HttpV0Employee])
        .tag(tag)
        .description("Updates an employee by id")
        .serverLogicRecoverErrors {
          case (_, (employeeId, api)) =>
            for {
              orig    <- employeeRepo.unsafeGet(employeeId)
              _       <- employeeRepo.update(draft(orig, api))
              updated <- employeeRepo.unsafeGet(employeeId)
            } yield httpEmployeeFrom(updated)
        }

      case class BatchUpdateData(data: Part[File])
      val batchUpdate = securedEndpoint.patch
        .in("api" / "v0" / "employee")
        .in(multipartBody[BatchUpdateData])
        .out(jsonBody[HttpV0BatchUpdateResponse])
        .tag(tag)
        .description("Batch update for employees")
        .serverLogicRecoverErrors {
          case (_, parts) =>
            for {
              invalid <- {
                val reader = parts.data.body.asCsvReader[CsvEmployee](CsvConfiguration.rfc)

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

      val endpoints             = List(get, update, batchUpdate)
      override val openApiDoc   = endpoints.toOpenAPI("", "")
      override val http4sRoutes = endpoints.map(_.toRoutes)
    }
  }

  private case class CsvEmployee(name: String, position: String, email: String, sex: Sex, companyId: CompanyId)
}
