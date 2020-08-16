package com.github.vsuharnikov.barbarissa.backend.queue.app

import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.{RIO, URIO}

class QueueHttpApiRoutes[R <: AbsenceQueue] extends JsonEntitiesEncoding[RIO[R, *]] {
  type HttpIO[A]   = RIO[R, A]
  type HttpURIO[A] = URIO[R, A]

  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    "Add an item to the queue" **
      "queue" @@
        POST / "api" / "v0" / "queue" / "add" ^ circeJsonDecoder[HttpV0AbsenceQueueItem] |>> { (api: HttpV0AbsenceQueueItem) =>
      val draft = AbsenceQueueItem(
        absenceId = AbsenceId(api.absenceId),
        done = api.done,
        claimSent = api.done,
        appointmentCreated = api.appointmentCreated,
        retries = api.retries
      )
      AbsenceQueue.add(List(draft)) *> Ok("Added")
    }
  }
}
