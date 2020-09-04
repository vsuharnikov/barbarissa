package com.github.vsuharnikov.barbarissa.backend.employee.app.entities

import io.circe.generic.extras.ConfiguredJsonCodec

// TODO patch
@ConfiguredJsonCodec case class HttpV0UpdateEmployee(
    localizedName: String,
    companyId: String,
    position: String
)
