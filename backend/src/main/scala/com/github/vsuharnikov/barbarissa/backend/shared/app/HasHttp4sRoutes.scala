package com.github.vsuharnikov.barbarissa.backend.shared.app

import org.http4s.HttpRoutes
import sttp.tapir.openapi.OpenAPI
import zio.Task

trait HasHttp4sRoutes extends Serializable {
  def http4sRoutes: HttpRoutes[Task]
  def openApiDocs: OpenAPI
}
