package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import cats.syntax.apply._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraBasicUserData, JiraExtendedUserData, JiraGetExtendedUserData}
import com.github.vsuharnikov.barbarissa.backend.employee.{CompanyId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Sex, error}
import io.circe.syntax._
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.syntax.all._
import zio.interop.catz._
import zio.{Task, ZIO, ZLayer}

object EmployeeJiraRepo {
  case class Config(credentials: BasicCredentials) // TODO server

  val live = ZLayer.fromServices[Config, Client[Task], EmployeeRepo.Service] { (config, client) =>
    new EmployeeRepo.Service with JsonSupport[Task] {
      private val commonHeaders = Headers.of(Authorization(BasicCredentials(config.credentials.username, config.credentials.password)))

      override def update(draft: Employee): ZIO[Any, error.RepoError, Unit] = {
        val uri = extendedDataUri(draft.id.asString)
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

        run(req)(_ => Task.unit)
      }

      override def get(by: EmployeeId): ZIO[Any, error.RepoError, Employee] =
        (
          get[JiraBasicUserData](basicDataUri(by.asString)),
          get[JiraGetExtendedUserData](extendedDataUri(by.asString)).foldM(
            {
              case error.RepoRecordNotFound => ZIO.succeed(JiraExtendedUserData.empty)
              case error                    => ZIO.fail(error)
            },
            x => ZIO.succeed(x.value)
          )
        ).mapN(toDomain)

      private def get[T](uri: Uri)(implicit ed: EntityDecoder[Task, T]): ZIO[Any, error.RepoError, T] =
        run(Request[Task](uri = uri, headers = commonHeaders))(_.as[T])

      private def run[T](req: Request[Task])(f: Response[Task] => Task[T]): ZIO[Any, error.RepoError, T] =
        client
          .run(req)
          .use {
            case Status.Successful(resp) => f(resp)
            case Status.ClientError(resp) =>
              Task.fail(ForwardError {
                if (resp.status == Status.NotFound) error.RepoRecordNotFound
                else error.RepoUnknown
              })
            case Status.ServerError(_) => Task.fail(ForwardError(error.RepoNotAvailable))
            case _                     => Task.fail(ForwardError(error.RepoUnknown))
          }
          .catchAll {
            case ForwardError(error) => ZIO.fail(error)
            case _                   => ZIO.fail(error.RepoUnknown)
          }
    }
  }

  private def extendedDataUri(userName: String): Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/user/properties/hr".withQueryParam("username", userName)

  private def basicDataUri(userName: String): Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/user".withQueryParam("username", userName)

  private def toDomain(basic: JiraBasicUserData, extended: JiraExtendedUserData): Employee = Employee(
    id = EmployeeId(basic.name),
    name = basic.displayName,
    localizedName = extended.localizedName,
    email = basic.emailAddress,
    companyId = extended.companyId.map(CompanyId),
    position = extended.position,
    sex = extended.sex.map {
      case "male"   => Sex.Male
      case "female" => Sex.Female
    }
  )
}

case class ForwardError(x: error.RepoError) extends RuntimeException(s"Error: $x", null, true, false)
