package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.Version
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonEntitiesEncoding
import com.github.vsuharnikov.barbarissa.backend.shared.domain.DomainError
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.entities.{
  JiraBasicUserData,
  JiraExtendedUserData,
  JiraGetExtendedUserData,
  JiraSearchFailedResult,
  JiraSearchRequest,
  JiraSearchResult
}
import io.circe.syntax._
import org.http4s.Method.{POST, PUT}
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, Authorization, `Content-Type`, `User-Agent`}
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.interop.catz._
import zio.logging._
import zio.macros.accessible

@accessible
object JiraApi extends Serializable {
  trait Service extends Serializable {
    def getUserBasicData(username: String): Task[Option[JiraBasicUserData]]
    def searchUsers(email: String): Task[List[JiraBasicUserData]]

    def setUserExtendedData(username: String, draft: JiraExtendedUserData): Task[Unit]
    def getUserExtendedData(username: String): Task[Option[JiraExtendedUserData]]

    def searchIssues(req: JiraSearchRequest): Task[JiraSearchResult]
  }

  case class Config(restApi: Uri, credentials: BasicCredentials, retryPolicy: RetryPolicyConfig)
  case class RetryPolicyConfig(recur: Int, space: Duration)

  type Dependencies = Has[Config] with Clock with Logging with Has[Client[Task]]

  val live: ZLayer[Dependencies, Nothing, Has[Service]] = ZIO
    .access[Dependencies] { env =>
      val config = env.get[Config]
      val client = env.get[Client[Task]]

      new Service with JsonEntitiesEncoding[Task] {
        private val jiraUri = new JiraUri(config.restApi)

        private val commonHeaders = Headers.of(
          Authorization(BasicCredentials(config.credentials.username, config.credentials.password)),
          `User-Agent`(AgentProduct("barbarissa", Version.VersionString.some))
        )

        override def getUserBasicData(username: String): Task[Option[JiraBasicUserData]] =
          get[JiraBasicUserData](jiraUri.userBasicData(username))

        override def searchUsers(email: String): Task[List[JiraBasicUserData]] =
          getMany[JiraBasicUserData](jiraUri.searchUser(email))

        override def setUserExtendedData(username: String, draft: JiraExtendedUserData): Task[Unit] = {
          val uri = jiraUri.userExtendedData(username)
          val req = Request[Task](PUT, uri, headers = commonHeaders)
            .withContentType(`Content-Type`(MediaType.application.json))
            .withEntity(draft.asJson)

          run(req)(_ => Task.unit).unit
        }

        override def getUserExtendedData(username: String): Task[Option[JiraExtendedUserData]] =
          get[JiraGetExtendedUserData](jiraUri.userExtendedData(username)).map(_.map(_.value))

        override def searchIssues(req: JiraSearchRequest): Task[JiraSearchResult] = {
          val httpReq = Request[Task](POST, jiraUri.searchIssue, headers = commonHeaders).withEntity(req)
          runWithError(httpReq) {
            case Status.Successful(x)  => x.as[JiraSearchResult]
            case Status.ServerError(_) => Task.fail(remoteCallFailed)
            case x =>
              if (x.status == Status.BadRequest) x.as[JiraSearchFailedResult].flatMap { x =>
                Task.fail(DomainError.JiraError(x.errorMessages))
              } else Task.fail(DomainError.UnhandledError("Jira call failed"))
          }
        }

        private def get[T](uri: Uri)(implicit ed: EntityDecoder[Task, T]): Task[Option[T]] =
          run(Request[Task](uri = uri, headers = commonHeaders))(_.as[T])

        private def getMany[T](uri: Uri)(implicit ed: EntityDecoder[Task, List[T]]): Task[List[T]] =
          runMany[T](Request[Task](uri = uri, headers = commonHeaders))(_.as[List[T]])

        private val remoteCallFailed = DomainError.RemoteCallFailed("Jira")
        private def run[T](req: Request[Task])(f: Response[Task] => Task[T]): Task[Option[T]] =
          runWithError(req) {
            case Status.Successful(x)  => f(x).map(_.some)
            case Status.ServerError(_) => Task.fail(remoteCallFailed)
            case x =>
              if (x.status == Status.NotFound) Task.succeed(none)
              else Task.fail(DomainError.UnhandledError("Jira call failed"))
          }

        private def runMany[T](req: Request[Task])(f: Response[Task] => Task[List[T]]): Task[List[T]] =
          runWithError(req) {
            case Status.Successful(x)  => f(x)
            case Status.ServerError(_) => Task.fail(remoteCallFailed)
            case _                     => Task.fail(DomainError.UnhandledError("Jira call failed"))
          }

        private def runWithError[T](req: Request[Task])(f: Response[Task] => Task[T]): Task[T] =
          client
            .run(req)
            .use(f)
            .retry(retryPolicy)
            .provide(env)

        private val retryPolicy: Schedule[Any, Throwable, Unit] = {
          Schedule.recurs(config.retryPolicy.recur) &&
          Schedule.spaced(config.retryPolicy.space) &&
          Schedule.recurWhile[Throwable] {
            case _: DomainError.UnhandledError | _: DomainError.JiraError => false
            case _                                                        => true
          }
        }.unit.provide(env)
      }
    }
    .toLayer

  class JiraUri(restApi: Uri) {
    def userBasicData(username: String): Uri = restApi / "2" / "user" withQueryParams Map("username" -> username)

    def searchUser(email: String): Uri = restApi / "2" / "user" / "search" withQueryParams Map("username" -> email)

    def userExtendedData(username: String): Uri = restApi / "2" / "user" / "properties" / "barbarissa" withQueryParams Map("username" -> username)

    val searchIssue = restApi / "2" / "search"
  }
}
