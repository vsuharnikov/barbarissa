package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import cats.syntax.apply._
import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraBasicUserData, JiraExtendedUserData, JiraGetExtendedUserData}
import com.github.vsuharnikov.barbarissa.backend.employee.{CompanyId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Sex, error}
import io.circe.syntax._
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.syntax.all._
import zio.interop.catz._
import zio.{Task, ZLayer}

object JiraEmployeeRepo {
  case class Config(credentials: BasicCredentials) // TODO server

  val live = ZLayer.fromServices[Config, Client[Task], EmployeeRepo.Service] { (config, client) =>
    new EmployeeRepo.Service with JsonSupport[Task] {
      private val commonHeaders = Headers.of(Authorization(BasicCredentials(config.credentials.username, config.credentials.password)))

      override def update(draft: Employee): Task[Unit] = {
        val uri = extendedDataUri(draft.employeeId.asString)
        val req = Request[Task](PUT, uri, headers = commonHeaders)
          .withContentType(`Content-Type`(MediaType.application.json))
          .withEntity(
            JiraExtendedUserData(
              localizedName = draft.localizedName,
              position = draft.position,
              companyId = draft.companyId.map(_.asString),
              sex = draft.sex.map {
                case Sex.Male   => "male"
                case Sex.Female => "female"
              }
            ).asJson
          )

        run(req)(_ => Task.unit).unit
      }

      override def get(by: EmployeeId): Task[Option[Employee]] =
        (
          get[JiraBasicUserData](basicDataUri(by.asString)),
          get[JiraGetExtendedUserData](extendedDataUri(by.asString)).map(_.fold(JiraExtendedUserData.empty)(_.value))
        ).mapN(toDomain)

      private def get[T](uri: Uri)(implicit ed: EntityDecoder[Task, T]): Task[Option[T]] =
        run(Request[Task](uri = uri, headers = commonHeaders))(_.as[T])

      private def run[T](req: Request[Task])(f: Response[Task] => Task[T]): Task[Option[T]] =
        client
          .run(req)
          .use {
            case Status.Successful(resp) => f(resp).map(_.some)
            case Status.ClientError(resp) =>
              if (resp.status == Status.NotFound) Task.succeed(none)
              else Task.fail(ForwardError(error.RepoUnknown))
            case Status.ServerError(_) => Task.fail(ForwardError(error.RepoNotAvailable))
            case _                     => Task.fail(ForwardError(error.RepoUnknown))
          }
//          .catchAll {
//            case ForwardError(error) => ZIO.fail(error)
//            case _                   => ZIO.fail(error.RepoUnknown)
//          }
    }
  }

  private def basicDataUri(key: String): Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/user".withQueryParam("username", key)

  private def extendedDataUri(key: String): Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/user/properties/hr".withQueryParam("username", key)

  private def toDomain(basic: Option[JiraBasicUserData], extended: JiraExtendedUserData): Option[Employee] = basic.map { basic =>
    Employee(
      employeeId = EmployeeId(basic.name),
      name = basic.displayName,
      localizedName = extended.localizedName,
      email = basic.emailAddress,
      companyId = extended.companyId.map(CompanyId(_)),
      position = extended.position,
      sex = extended.sex.map {
        case "male"   => Sex.Male
        case "female" => Sex.Female
      }
    )
  }
}
