package com.github.vsuharnikov.barbarissa.backend.shared.app

import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import sttp.tapir.openapi.OpenAPI
import zio.Task
import zio.interop.catz._

trait HasHttp4sRoutes extends Serializable {
  def http4sRoute: HttpRoutes[Task] = http4sRoutes.reduce(_ <+> _)
  def openApiDoc: OpenAPI

  def http4sRoutes: List[HttpRoutes[Task]]
}
