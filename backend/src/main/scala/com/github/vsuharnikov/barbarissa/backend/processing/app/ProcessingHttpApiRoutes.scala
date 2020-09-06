package com.github.vsuharnikov.barbarissa.backend.processing.app

import com.github.vsuharnikov.barbarissa.backend.HttpApiConfig
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

  val live = ZLayer.fromServices[HttpApiConfig, ProcessingService.Service, Service] { (config, processingService) =>
    new Service with TapirCommonEntities {
      val tag = "processing"

      val securedEndpoint = TapirSecuredEndpoint(config.apiKeyHashBytes)

      val start = securedEndpoint.post
        .in("api" / "v0" / "processing" / "start")
        .out(jsonBody[HttpMessage])
        .tag(tag)
        .description("Starts schedules")
        .serverLogicRecoverErrors { _ =>
          processingService.start.as(HttpMessage("Started"))
        }

      val refreshQueue = securedEndpoint.post
        .in("api" / "v0" / "processing" / "refreshQueue")
        .out(jsonBody[HttpMessage])
        .tag(tag)
        .description("Refreshes the queue")
        .serverLogicRecoverErrors { _ =>
          processingService.refreshQueue.as(HttpMessage("Started"))
        }

      val processCurrent = securedEndpoint.post
        .in("api" / "v0" / "processing" / "processCurrent")
        .out(jsonBody[HttpMessage])
        .tag(tag)
        .description("Process items in the queue")
        .serverLogicRecoverErrors { _ =>
          processingService.processQueue.as(HttpMessage("Started"))
        }

      val createClaim = securedEndpoint.post
        .in("api" / "v0" / "processing" / "createClaim" / "absence" / absenceIdPath)
        .out(byteArrayBody)
        .tag(tag)
        .description("Creates a claim for employee's absence")
        .serverLogicRecoverErrors { case (_, absenceId) => processingService.createClaim(absenceId) }

      val endpoints             = List(start, refreshQueue, processCurrent, createClaim)
      override val openApiDoc   = endpoints.toOpenAPI("", "")
      override val http4sRoutes = endpoints.map(_.toRoutes)
    }
  }
}
