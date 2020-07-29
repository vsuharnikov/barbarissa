package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceRepo.{GetAfterCursor, GetCursor}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Absence, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.JiraEmployeeRepo.Config
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraSearchRequest, JiraSearchResult, JiraSearchResultItem}
import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.syntax.all._
import zio.interop.catz._
import zio.{Task, ZIO, ZLayer}

object JiraAbsenceRepo {
  val live = ZLayer.fromServices[Config, Client[Task], AbsenceRepo.Service] { (config, client) =>
    new AbsenceRepo.Service with JsonSupport[Task] {
      private val commonHeaders = Headers.of(Authorization(BasicCredentials(config.credentials.username, config.credentials.password)))

      private val searchRequestFields = List(
        "reporter",
        "customfield_10439", // "Absence reason"
        "customfield_10437", // "Start Date"
        "customfield_10438" // "Quantiny (days)"
      )

      override def getByCursor(cursor: GetCursor): ZIO[Any, error.RepoError, (List[Absence], Option[GetCursor])] =
        get(
          JiraSearchRequest(
            jql = s"""reporter=${cursor.by.asString} AND project="HR Services" AND type=Absence AND "Absence reason" is not EMPTY ORDER BY key DESC""",
            startAt = cursor.startAt,
            maxResults = cursor.maxResults,
            searchRequestFields
          )) { resp =>
          (
            resp.issues.map(absenceFrom),
            nextCursor(resp).map { case (startAt, maxResults) => GetCursor(cursor.by, startAt, maxResults) }
          )
        }

      override def get(by: employee.EmployeeId, absenceId: AbsenceId): ZIO[Any, error.RepoError, Absence] =
        get(
          JiraSearchRequest(
            jql = s"""key=${absenceId.asString}""",
            startAt = 0,
            maxResults = 1,
            searchRequestFields
          )) { resp =>
          resp.issues.headOption.map(absenceFrom).get // TODO
        }

      override def getFromByCursor(cursor: GetAfterCursor): ZIO[Any, error.RepoError, (List[Absence], Option[GetAfterCursor])] = {
        val absenceIdFragment = cursor.from.fold("")(x => s"AND type=Absence AND key > ${x.asString}")
        get(
          JiraSearchRequest(
            jql = s"""project="HR Services" $absenceIdFragment AND "Absence reason" IS NOT EMPTY ORDER BY key""",
            startAt = cursor.startAt,
            maxResults = cursor.maxResults,
            searchRequestFields
          )) { resp =>
          (
            resp.issues.map(absenceFrom),
            nextCursor(resp).map { case (startAt, maxResults) => GetAfterCursor(cursor.from, startAt, maxResults) }
          )
        }
      }

      private def get[T](req: JiraSearchRequest)(f: JiraSearchResult => T): ZIO[Any, error.RepoError, T] = {
        val httpReq = Request[Task](POST, searchAbsenceUri, headers = commonHeaders).withEntity(req)
        run(httpReq)(_.as[JiraSearchResult]).map(f)
      }

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

      def nextCursor(resp: JiraSearchResult): Option[(Int, Int)] = {
        val retrieved = resp.startAt + resp.issues.size
        if (retrieved < resp.total) (retrieved, resp.maxResults).some else none
      }
    }
  }

  private val searchAbsenceUri: Uri =
    uri"https://jira.wavesplatform.com/rest/api/2/search"

  def absenceFrom(jira: JiraSearchResultItem): Absence = Absence(
    id = AbsenceId(jira.key),
    employeeId = EmployeeId(jira.fields.reporter.name),
    from = jira.fields.startDate,
    daysQuantity = jira.fields.daysQuantity.toInt,
    reason = Absence.Reason(
      id = AbsenceReasonId(jira.fields.absenceReason.id),
      name = jira.fields.absenceReason.value
    )
  )
}
