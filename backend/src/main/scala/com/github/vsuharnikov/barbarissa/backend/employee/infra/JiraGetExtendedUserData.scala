package com.github.vsuharnikov.barbarissa.backend.employee.infra

import io.circe.generic.JsonCodec

@JsonCodec case class JiraGetExtendedUserData(value: JiraExtendedUserData)
