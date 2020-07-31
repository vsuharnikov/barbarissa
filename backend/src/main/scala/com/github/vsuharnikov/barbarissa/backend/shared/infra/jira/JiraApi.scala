package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.Version
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities._
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.{HttpClientException, HttpServerException}
import io.circe.syntax._
import org.http4s.Method.{POST, PUT}
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, Authorization, `Content-Type`, `User-Agent`}
import zio.clock.Clock
import zio.duration.Duration
import zio.interop.catz._
import zio.macros.accessible
import zio.{Has, Schedule, Task, ZIO, ZLayer}

@accessible
object JiraApi extends Serializable {
  trait Service extends Serializable {
    def getUserBasicData(username: String): Task[Option[JiraBasicUserData]]

    def setUserExtendedData(username: String, draft: JiraExtendedUserData): Task[Unit]
    def getUserExtendedData(username: String): Task[Option[JiraExtendedUserData]]

    def searchIssue[T](req: JiraSearchRequest): Task[JiraSearchResult]
  }

  case class Config(restApi: Uri, credentials: BasicCredentials, retryPolicy: RetryPolicy)
  case class RetryPolicy(recur: Int, space: Duration)

  val live = ZLayer.fromServices[Config, Clock.Service, Client[Task], Service] { (config, clock, client) =>
    new Service with JsonSupport[Task] {
      private val jiraUri = new JiraUri(config.restApi)

      private val commonHeaders = Headers.of(
        Authorization(BasicCredentials(config.credentials.username, config.credentials.password)),
        `User-Agent`(AgentProduct("barbarissa", Version.VersionString.some))
      )

      override def getUserBasicData(username: String): Task[Option[JiraBasicUserData]] =
        get[JiraBasicUserData](jiraUri.userBasicData(username))

      override def setUserExtendedData(username: String, draft: JiraExtendedUserData): Task[Unit] = {
        val uri = jiraUri.userExtendedData(username)
        val req = Request[Task](PUT, uri, headers = commonHeaders)
          .withContentType(`Content-Type`(MediaType.application.json))
          .withEntity(draft.asJson)

        run(req)(_ => Task.unit).unit
      }

      override def getUserExtendedData(username: String): Task[Option[JiraExtendedUserData]] =
        get[JiraGetExtendedUserData](jiraUri.userExtendedData(username)).map(_.map(_.value))

      override def searchIssue[T](req: JiraSearchRequest): Task[JiraSearchResult] = {
        val httpReq = Request[Task](POST, jiraUri.searchIssue, headers = commonHeaders).withEntity(req)
        run(httpReq)(_.as[JiraSearchResult]).flatMap {
          // It is impossible. So treat 404 as a client error
          case None    => ZIO.fail(new HttpClientException(Status.NotFound))
          case Some(x) => ZIO.succeed(x)
        }
      }

      private def get[T](uri: Uri)(implicit ed: EntityDecoder[Task, T]): Task[Option[T]] =
        run(Request[Task](uri = uri, headers = commonHeaders))(_.as[T])

      private def run[T](req: Request[Task])(f: Response[Task] => Task[T]): Task[Option[T]] =
        client
          .run(req)
          .use {
            case Status.Successful(x)  => f(x).map(_.some)
            case Status.ServerError(x) => Task.fail(new HttpServerException(x.status))
            case x =>
              if (x.status == Status.NotFound) Task.succeed(none)
              else Task.fail(new HttpClientException(x.status))
          }
          .retry(retryPolicy)

      private val retryPolicy: Schedule[Any, Throwable, Unit] = {
        Schedule.recurs(config.retryPolicy.recur) &&
        Schedule.spaced(config.retryPolicy.space) &&
        Schedule.doWhile[Throwable] {
          case _: HttpClientException => false
          case _                      => true
        }
      }.unit.provide(Has(clock))
    }
  }

  class JiraUri(restApi: Uri) {
    def userBasicData(username: String): Uri = restApi / "2" / "user" withQueryParams Map("username" -> username)

    def userExtendedData(username: String): Uri = restApi / "2" / "user" / "properties" / "hr" withQueryParams Map("username" -> username)

    val searchIssue = restApi / "2" / "search"
  }
}
