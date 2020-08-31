package com.github.vsuharnikov.barbarissa.backend.processing.app

import com.github.vsuharnikov.barbarissa.backend.processing.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceId
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZLayer}

@accessible
object ProcessingHttpApiRoutes extends Serializable {
  trait Service extends HasRoutes

  val live = ZLayer.fromService[ProcessingService.Service, Service] { processingService =>
    new Service with JsonEntitiesEncoding[Task] {
      private val swaggerSyntax = new SwaggerSupport[Task] {}
      import swaggerSyntax._

      override val rhoRoutes: RhoRoutes[Task] = new RhoRoutes[Task] {
        val parsers = new RoutesParsers[Task]()
        import parsers._

        "Starts schedules" **
          "processing" @@
            POST / "api" / "v0" / "processing" / "start" |>> {
          processingService.start *> Ok("Started")
        }

        "Refreshes the queue" **
          "processing" @@
            POST / "api" / "v0" / "processing" / "refreshQueue" |>> {
          processingService.refreshQueue *> Ok("Done") // TODO
        }

        "Process items in the queue" **
          "processing" @@
            POST / "api" / "v0" / "processing" / "processQueue" |>> {
          processingService.processQueue *> Ok("Processed")
        }

        "Creates a claim for employee's absence" **
          "absence" @@
            POST / "api" / "v0" / "processing" / "createClaim" /
          "absence" / pathVar[AbsenceId]("absenceId") |>> { (aid: AbsenceId) =>
          processingService.createClaim(aid).flatMap(Ok(_))
        }
      }
    }
  }
}
