package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities

import io.circe.generic.JsonCodec

@JsonCodec case class JiraBasicUserData(name: String, displayName: String, emailAddress: String)
