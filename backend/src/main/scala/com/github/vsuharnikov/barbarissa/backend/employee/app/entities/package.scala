package com.github.vsuharnikov.barbarissa.backend.employee.app

import io.circe.generic.extras.Configuration

package object entities {
  private[entities] implicit val circeConfig: Configuration = Configuration.default.withDefaults
}
