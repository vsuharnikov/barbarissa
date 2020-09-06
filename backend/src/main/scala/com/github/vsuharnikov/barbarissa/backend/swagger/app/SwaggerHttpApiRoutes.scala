package com.github.vsuharnikov.barbarissa.backend.swagger.app

import cats.effect.Blocker
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import zio.Task
import zio.internal.Executor
import zio.interop.catz._
import zio.macros.accessible

@accessible
object SwaggerHttpApiRoutes extends Serializable {
  trait Service extends Serializable {
    def routes: HttpRoutes[Task]
  }

  def live(blockingExecutor: Executor, yaml: String) = new Service {
    private val dsl = Http4sDsl[Task]
    import dsl._

    private val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutor.asEC)

    private val swaggerHtmlResponse = static("swagger.html")
    private val yamlResponse        = Ok(yaml)

    override val routes = HttpRoutes.of[Task] {
      case GET -> Root / "docs"                          => swaggerHtmlResponse
      case GET -> Root / "api" / "docs" / "swagger.yaml" => yamlResponse
    }

    private def static(file: String): Task[Response[Task]] =
      StaticFile
        .fromResource[Task]("/" + file, blocker)
        .getOrElseF { Ok(s"$file not found") }
  }
}
