package com.github.vsuharnikov.barbarissa.backend.employee

import zio.logging.LogAnnotation

package object app {
  val requestIdLogAnnotation = LogAnnotation[String](
    name = "requestId",
    initialValue = "null",
    combine = (_, newValue) => newValue,
    render = identity
  )
}
