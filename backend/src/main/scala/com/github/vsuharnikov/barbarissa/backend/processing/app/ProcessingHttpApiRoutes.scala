package com.github.vsuharnikov.barbarissa.backend.processing.app

import com.github.vsuharnikov.barbarissa.backend.processing.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.ztapir._
import zio.ZLayer
import zio.interop.catz._
import zio.macros.accessible

@accessible
object ProcessingHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromService[ProcessingService.Service, Service] { processingService =>
    new Service with TapirCommonEntities {
      val tag = "processing"

      val start = endpoint.post
        .in("api" / "v0" / "processing" / "start")
        .out(jsonBody[HttpMessage])
        .errorOut(errorOut)
        .tag(tag)
        .description("Starts schedules")

      val startRoute = start.toRoutes { _ =>
        processingService.start.as(HttpMessage("Started"))
      }

      val refreshQueue = endpoint.post
        .in("api" / "v0" / "processing" / "refreshQueue")
        .out(jsonBody[HttpMessage])
        .errorOut(errorOut)
        .tag(tag)
        .description("Refreshes the queue")

      val refreshQueueRoute = refreshQueue.toRoutes { _ =>
        processingService.refreshQueue.as(HttpMessage("Started"))
      }

      val processCurrent = endpoint.post
        .in("api" / "v0" / "processing" / "processCurrent")
        .out(jsonBody[HttpMessage])
        .errorOut(errorOut)
        .tag(tag)
        .description("Process items in the queue")

      val processCurrentRoute = processCurrent.toRoutes { _ =>
        processingService.processQueue.as(HttpMessage("Started"))
      }

      val createClaim = endpoint.post
        .in("api" / "v0" / "processing" / "createClaim" / "absence" / absenceIdPath)
        .out(byteArrayBody)
        .errorOut(errorOut)
        .tag(tag)
        .description("Creates a claim for employee's absence")

      val createClaimRoute = createClaim.toRoutes { absenceId =>
        processingService.createClaim(absenceId)
      }

      override val openApiDoc   = List(start, refreshQueue, processCurrent, createClaim).toOpenAPI("", "")
      override val http4sRoutes = List(startRoute, refreshQueueRoute, processCurrentRoute, createClaimRoute)
    }
  }
}
