package com.github.vsuharnikov.barbarissa.backend.employee

import zio.Has
import zio.logging.LogAnnotation

package object app {
  type EmployeeHttpApiRoutes    = Has[EmployeeHttpApiRoutes.Service]
  type EmployeeHttpApiRhoRoutes = Has[EmployeeHttpApiRhoRoutes.Service]

  val requestIdLogAnnotation = LogAnnotation[String](
    name = "requestId",
    initialValue = "null",
    combine = (_, newValue) => newValue,
    render = identity
  )
}
