package com.github.vsuharnikov.barbarissa.backend.absence.infra.jira

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.absence.domain.AbsenceRepo.{GetAfterCursor, GetCursor}
import com.github.vsuharnikov.barbarissa.backend.absence.domain.{Absence, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonEntitiesEncoding
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, AbsenceReasonId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.entities.{JiraSearchRequest, JiraSearchResult, JiraSearchResultItem}
import zio.logging.{Logging, log}
import zio.{Has, Task, ZIO, ZLayer}

object JiraAbsenceRepo {
  val live: ZLayer[Logging with JiraApi, Nothing, Has[AbsenceRepo.Service]] = ZIO
    .access[Logging with JiraApi] { env =>
      new AbsenceRepo.Service with JsonEntitiesEncoding[Task] {
        private val searchRequestFields = List(
          "reporter",
          "customfield_10439", // "Absence reason"
          "customfield_10437", // "Start Date"
          "customfield_10438" // "Quantiny (days)"
        )

        private val nonEmptyFragment = """"Absence reason" IS NOT EMPTY AND "Start Date" IS NOT EMPTY AND "Quantiny (days)" IS NOT EMPTY"""

        override def get(absenceId: AbsenceId): Task[Option[Absence]] = {
          log.info(s"Updating '${absenceId.asString}'") *>
            JiraApi
              .searchIssues(
                JiraSearchRequest(
                  jql = s"""key=${absenceId.asString} AND $nonEmptyFragment""",
                  startAt = 0,
                  maxResults = 1,
                  searchRequestFields
                ))
              .map { resp =>
                resp.issues.headOption.map(absenceFrom)
              }
        }.provide(env)

        override def getByCursor(cursor: GetCursor): Task[(List[Absence], Option[GetCursor])] = {
          log.info(s"Getting by cursor '$cursor'") *>
            JiraApi
              .searchIssues(JiraSearchRequest(
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
        }.provide(env)

        override def getFromByCursor(cursor: GetAfterCursor): Task[(List[Absence], Option[GetAfterCursor])] = {
          val absenceIdFragment = cursor.from.fold("")(x => s"AND type=Absence AND key > ${x.asString}")
          log.info(s"Getting from by cursor '$cursor'") *>
            JiraApi
              .searchIssues(JiraSearchRequest(
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
        }.provide(env)

        def nextCursor(resp: JiraSearchResult): Option[(Int, Int)] = {
          val retrieved = resp.startAt + resp.issues.size
          if (retrieved < resp.total) (retrieved, resp.maxResults).some else none
        }
      }
    }
    .toLayer

  def absenceFrom(jira: JiraSearchResultItem): Absence = Absence(
    absenceId = AbsenceId(jira.key),
    employeeId = EmployeeId(jira.fields.reporter.name),
    from = jira.fields.startDate,
    daysQuantity = jira.fields.daysQuantity.toInt,
    reasonId = AbsenceReasonId(jira.fields.absenceReason.id)
  )
}
