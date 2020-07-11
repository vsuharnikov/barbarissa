package com.github.vsuharnikov.barbarissa.backend.employee.app

import io.circe.generic.JsonCodec

@JsonCodec case class HttpV0UpdateEmployee(
    localizedName: String,
    position: String
)
