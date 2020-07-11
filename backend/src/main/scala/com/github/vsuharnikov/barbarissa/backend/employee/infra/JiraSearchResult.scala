package com.github.vsuharnikov.barbarissa.backend.employee.infra

import java.time.LocalDate

import io.circe.generic.JsonCodec
import io.circe.generic.extras._

@JsonCodec case class JiraSearchRequest(jql: String, startAt: Int, maxResults: Int, fields: List[String])

@JsonCodec case class JiraSearchResult(startAt: Int, maxResults: Int, total: Int, issues: List[JiraSearchResultItem])
@JsonCodec case class JiraSearchResultItem(key: String, self: String, fields: JiraSearchResultItemFields)
@ConfiguredJsonCodec case class JiraSearchResultItemFields(
    @JsonKey("customfield_10437") startDate: LocalDate,
    @JsonKey("customfield_10438") daysQuantity: Float,
    @JsonKey("customfield_10439") absenceReason: JiraSearchResultItemAbsenceReasonField
)
@JsonCodec case class JiraSearchResultItemAbsenceReasonField(id: String, value: String)
