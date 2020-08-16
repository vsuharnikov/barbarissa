package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira

import io.circe.generic.extras.Configuration

package object entities {
  implicit val circeConfig: Configuration = Configuration.default
}
