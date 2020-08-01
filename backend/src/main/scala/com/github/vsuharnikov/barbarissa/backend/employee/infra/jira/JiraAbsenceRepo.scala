package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.app.JsonEntitiesEncoding
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceRepo.{GetAfterCursor, GetCursor}
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Absence, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraSearchRequest, JiraSearchResult, JiraSearchResultItem}
import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import zio.{Task, ZLayer}

object JiraAbsenceRepo {
  val live = ZLayer.fromService[JiraApi.Service, AbsenceRepo.Service] { api =>
    new AbsenceRepo.Service with JsonEntitiesEncoding[Task] {
      private val searchRequestFields = List(
        "reporter",
        "customfield_10439", // "Absence reason"
        "customfield_10437", // "Start Date"
        "customfield_10438" // "Quantiny (days)"
      )

      private val nonEmptyFragment = """"Absence reason" IS NOT EMPTY AND "Start Date" IS NOT EMPTY AND "Quantiny (days)" IS NOT EMPTY"""

      override def get(absenceId: AbsenceId): Task[Option[Absence]] =
        api
          .searchIssue(
            JiraSearchRequest(
              jql = s"""key=${absenceId.asString} AND $nonEmptyFragment""",
              startAt = 0,
              maxResults = 1,
              searchRequestFields
            ))
          .map { resp =>
            resp.issues.headOption.map(absenceFrom)
          }

      override def getByCursor(cursor: GetCursor): Task[(List[Absence], Option[GetCursor])] =
        api
          .searchIssue(
            JiraSearchRequest(
              jql = s"""reporter=${cursor.by.asString} AND project="HR Services" AND type=Absence AND $nonEmptyFragment ORDER BY key DESC""",
              startAt = cursor.startAt,
              maxResults = cursor.maxResults,
              searchRequestFields
            ))
          .map { resp =>
            (
              resp.issues.map(absenceFrom),
              nextCursor(resp).map { case (startAt, maxResults) => GetCursor(cursor.by, startAt, maxResults) }
            )
          }

      override def getFromByCursor(cursor: GetAfterCursor): Task[(List[Absence], Option[GetAfterCursor])] = {
        val absenceIdFragment = cursor.from.fold("")(x => s"AND type=Absence AND key > ${x.asString}")
        api
          .searchIssue(
            JiraSearchRequest(
              jql = s"""project="HR Services" $absenceIdFragment AND $nonEmptyFragment ORDER BY key""",
              startAt = cursor.startAt,
              maxResults = cursor.maxResults,
              searchRequestFields
            ))
          .map { resp =>
            (
              resp.issues.map(absenceFrom),
              nextCursor(resp).map { case (startAt, maxResults) => GetAfterCursor(cursor.from, startAt, maxResults) }
            )
          }
      }

      def nextCursor(resp: JiraSearchResult): Option[(Int, Int)] = {
        val retrieved = resp.startAt + resp.issues.size
        if (retrieved < resp.total) (retrieved, resp.maxResults).some else none
      }
    }
  }

  def absenceFrom(jira: JiraSearchResultItem): Absence = Absence(
    absenceId = AbsenceId(jira.key),
    employeeId = EmployeeId(jira.fields.reporter.name),
    from = jira.fields.startDate,
    daysQuantity = jira.fields.daysQuantity.toInt,
    reasonId = AbsenceReasonId(jira.fields.absenceReason.id)
  )
}
