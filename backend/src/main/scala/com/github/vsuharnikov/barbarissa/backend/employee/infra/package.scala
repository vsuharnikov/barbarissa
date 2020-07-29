package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.generic.extras.Configuration
import zio.Has

package object infra {
  type ProcessingService = Has[ProcessingService.Service]

  implicit val circeConfig: Configuration = Configuration.default
}
