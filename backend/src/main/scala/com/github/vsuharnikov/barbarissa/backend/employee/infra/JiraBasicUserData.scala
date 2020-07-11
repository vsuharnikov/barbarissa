package com.github.vsuharnikov.barbarissa.backend.employee.infra

import io.circe.generic.JsonCodec

@JsonCodec case class JiraBasicUserData(name: String, displayName: String, emailAddress: String)
