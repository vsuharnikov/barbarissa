package com.github.vsuharnikov.barbarissa.backend.swagger.app

import cats.effect.Blocker
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.{HttpRoutes, Request, StaticFile}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZLayer}
import org.http4s._
import org.http4s.dsl.io._

@accessible
object SwaggerHttpApiRoutes extends Serializable {
  trait Service extends Serializable {
    def routes: HttpRoutes[Task]
  }

  val live = ZLayer.fromService[Blocking.Service, Service] { blocking =>
    new Service with JsonEntitiesEncoding[Task] {
      val blocker = Blocker.liftExecutionContext(blocking.blockingExecutor.asEC)

      override val routes = HttpRoutes.of[Task] {
        case request @ GET -> Root / "docs" => static("swagger.html", request)
      }

      def static(file: String, request: Request[Task]) =
        StaticFile
          .fromResource("/" + file, blocker, Some(request))
          .getOrElse { Response[Task](Status.NotFound).withEntity("Not found") }
    }
  }
}
