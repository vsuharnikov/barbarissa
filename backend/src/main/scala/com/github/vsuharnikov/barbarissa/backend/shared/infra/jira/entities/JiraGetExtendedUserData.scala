package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.entities

import io.circe.generic.JsonCodec

@JsonCodec case class JiraGetExtendedUserData(value: JiraExtendedUserData)
