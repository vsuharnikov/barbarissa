package com.github.vsuharnikov.barbarissa.backend.employee.infra

import io.circe.generic.JsonCodec

@JsonCodec case class JiraExtendedUserData(localizedName: Option[String], position: Option[String])
object JiraExtendedUserData {
  val empty = JiraExtendedUserData(None, None)
}
