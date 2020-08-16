package com.github.vsuharnikov.barbarissa.backend.processing.app

import com.github.vsuharnikov.barbarissa.backend.processing.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceId
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.RIO
import zio.interop.catz._

class ProcessingHttpApiRoutes[R <: ProcessingService] extends ApiRoutes[R] with JsonEntitiesEncoding[RIO[R, *]] {
  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  override val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]()
    import parsers._

    "Refreshes the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "refreshQueue" |>> {
      ProcessingService.refreshQueue *> Ok("Done") // TODO
    }

    "Process items in the queue" **
      "processing" @@
        POST / "api" / "v0" / "processing" / "processQueue" |>> {
      ProcessingService.processQueue *> Ok("Processed")
    }

    "Creates a claim for employee's absence" **
      "absence" @@
        POST / "api" / "v0" / "processing" / "createClaim" /
      "absence" / pathVar[AbsenceId]("absenceId") |>> { (aid: AbsenceId) =>
      ProcessingService.createClaim(aid).map(Ok(_))
    }
  }
}
