package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Absence, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.EmployeeJiraRepo.Config
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraSearchRequest, JiraSearchResult}
import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.syntax.all._
import zio.interop.catz._
import zio.{Task, ZIO, ZLayer}

object AbsenceJiraRepo {
  val live = ZLayer.fromServices[Config, Client[Task], AbsenceRepo.Service] { (config, client) =>
    new AbsenceRepo.Service with JsonSupport[Task] {
      private val commonHeaders = Headers.of(Authorization(BasicCredentials(config.credentials.username, config.credentials.password)))

      private val searchRequestFields = List(
        "customfield_10439", // "Absence reason"
        "customfield_10437", // "Start Date"
        "customfield_10438" // "Quantiny (days)"
      )

      override def get(by: employee.EmployeeId): ZIO[Any, error.RepoError, List[Absence]] = {
        val req = JiraSearchRequest(
          jql = s"""reporter=${by.asString} AND project="HR Services" AND type=Absence ORDER BY created DESC""",
          startAt = 0,
          maxResults = 3,
          searchRequestFields
        )
        val httpReq = Request[Task](POST, searchAbsenceUri, headers = commonHeaders).withEntity(req)
        run(httpReq)(_.as[JiraSearchResult]).map { resp =>
          resp.issues.map { jira =>
            Absence(
              id = AbsenceId(jira.key),
              from = jira.fields.startDate,
              daysQuantity = jira.fields.daysQuantity.toInt,
              reason = Absence.Reason(
                id = AbsenceReasonId(jira.fields.absenceReason.id),
                name = jira.fields.absenceReason.value
              )
            )
          }
        }
      }

      // TODO
      override def get(by: employee.EmployeeId, absenceId: AbsenceId): ZIO[Any, error.RepoError, Absence] = {
        val req = JiraSearchRequest(
          jql = s"""key=${absenceId.asString}""",
          startAt = 0,
          maxResults = 1,
          searchRequestFields
        )
        val httpReq = Request[Task](POST, searchAbsenceUri, headers = commonHeaders).withEntity(req)
        run(httpReq)(_.as[JiraSearchResult]).map { resp =>
          resp.issues.headOption.map { jira =>
            Absence(
              id = AbsenceId(jira.key),
              from = jira.fields.startDate,
              daysQuantity = jira.fields.daysQuantity.toInt,
              reason = Absence.Reason(
                id = AbsenceReasonId(jira.fields.absenceReason.id),
                name = jira.fields.absenceReason.value
              )
            )
          }.get // TODO
        }
      }

      private def get[T](uri: Uri)(implicit ed: EntityDecoder[Task, T]): ZIO[Any, error.RepoError, T] =
        run(Request[Task](uri = uri, headers = commonHeaders))(_.as[T])

      private def run[T](req: Request[Task])(f: Response[Task] => Task[T]): ZIO[Any, error.RepoError, T] =
        client
          .run(req)
          .use {
            case Status.Successful(resp) =>
              println(resp)
              f(resp)
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

  private val searchAbsenceUri: Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/search"
}
