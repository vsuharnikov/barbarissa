package com.github.vsuharnikov.barbarissa.backend.employee.app

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0UpdateEmployee(
    localizedName: String,
    companyId: String,
    position: String
)
