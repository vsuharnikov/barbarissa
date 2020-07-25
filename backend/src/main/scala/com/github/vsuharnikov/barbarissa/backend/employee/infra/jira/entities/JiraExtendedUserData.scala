package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities

import io.circe.generic.JsonCodec

@JsonCodec case class JiraExtendedUserData(
    localizedName: Option[String],
    companyId: Option[String],
    position: Option[String],
    sex: Option[String]
)
object JiraExtendedUserData {
  val empty = JiraExtendedUserData(None, None, None, None)
}
