package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities

import io.circe.generic.JsonCodec

@JsonCodec case class JiraGetExtendedUserData(value: JiraExtendedUserData)
