package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.generic.extras.Configuration
import zio.logging.LogAnnotation

package object app {
  implicit val circeConfig: Configuration = Configuration.default.withDefaults

  val requestIdLogAnnotation = LogAnnotation[String](
    name = "requestId",
    initialValue = "null",
    combine = (_, newValue) => newValue,
    render = identity
  )
}
