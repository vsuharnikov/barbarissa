package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.generic.extras.Configuration

package object infra {
  implicit val circeConfig: Configuration = Configuration.default
}
