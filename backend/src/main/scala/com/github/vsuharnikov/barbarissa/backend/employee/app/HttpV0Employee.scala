package com.github.vsuharnikov.barbarissa.backend.employee.app

import io.circe.generic.JsonCodec

@JsonCodec case class HttpV0Employee(
    id: String,
    name: String,
    localizedName: Option[String],
    companyId: Option[String],
    email: String,
    position: Option[String]
)
