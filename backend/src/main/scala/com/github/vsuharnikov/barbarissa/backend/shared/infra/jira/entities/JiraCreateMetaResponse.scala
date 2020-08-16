package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.entities

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{ConfiguredJsonCodec, JsonKey}

@JsonCodec case class JiraCreateMetaResponse(projects: NonEmptyList[JiraCreateMetaResponse.Project])

object JiraCreateMetaResponse {
  @JsonCodec case class Project(issueTypes: NonEmptyList[IssueType])
  @JsonCodec case class IssueType(fields: Fields)
  @ConfiguredJsonCodec case class Fields(@JsonKey("customfield_10439") absenceReason: JiraAbsenceReason)
  @JsonCodec case class JiraAbsenceReason(allowedValues: NonEmptyList[JiraValue])
}
