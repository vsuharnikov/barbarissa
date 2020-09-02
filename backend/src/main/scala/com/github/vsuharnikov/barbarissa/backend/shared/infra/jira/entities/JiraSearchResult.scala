package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.entities

import java.time.{LocalDate, LocalDateTime}

import io.circe.generic.JsonCodec
import io.circe.generic.extras._

@JsonCodec case class JiraSearchRequest(jql: String, startAt: Int, maxResults: Int, fields: List[String])

@JsonCodec case class JiraSearchFailedResult(errorMessages: List[String])

@JsonCodec case class JiraSearchResult(startAt: Int, maxResults: Int, total: Int, issues: List[JiraSearchResultItem])
@JsonCodec case class JiraSearchResultItem(key: String, self: String, fields: JiraSearchResultItemFields)
@ConfiguredJsonCodec case class JiraSearchResultItemFields(
    reporter: JiraSearchResultItemReporterField,
    created: LocalDateTime,
    @JsonKey("customfield_10437") startDate: LocalDate,
    @JsonKey("customfield_10438") daysQuantity: Float,
    @JsonKey("customfield_10439") absenceReason: JiraValue
)

@JsonCodec case class JiraSearchResultItemReporterField(name: String)
