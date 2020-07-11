package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.generic.extras.Configuration

package object infra {
  implicit val config: Configuration = Configuration.default
}
