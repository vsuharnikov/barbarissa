package com.github.vsuharnikov.barbarissa.backend.absence.app

import io.circe.generic.extras.Configuration

package object entities {
  implicit val circeConfig: Configuration = Configuration.default.withDefaults
}
