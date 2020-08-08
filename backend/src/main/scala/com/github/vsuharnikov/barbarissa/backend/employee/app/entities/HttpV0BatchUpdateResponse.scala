package com.github.vsuharnikov.barbarissa.backend.employee.app.entities

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0BatchUpdateResponse(
    invalidLines: List[String]
                                     )
