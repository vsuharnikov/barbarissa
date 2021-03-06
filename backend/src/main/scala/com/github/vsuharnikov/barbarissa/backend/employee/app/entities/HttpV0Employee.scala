package com.github.vsuharnikov.barbarissa.backend.employee.app.entities

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0Employee(
    id: String,
    name: String,
    localizedName: Option[String],
    companyId: Option[String],
    email: String,
    position: Option[String]
)
