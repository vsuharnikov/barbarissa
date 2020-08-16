package com.github.vsuharnikov.barbarissa.backend.queue.app

import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZLayer}

@accessible
object QueueHttpApiRoutes extends Serializable {
  trait Service extends HasRoutes

  val live = ZLayer.fromService[AbsenceQueue.Service, Service] { absenceQueue =>
    new Service with JsonEntitiesEncoding[Task] {
      private val swaggerSyntax = new SwaggerSupport[Task] {}
      import swaggerSyntax._

      val rhoRoutes: RhoRoutes[Task] = new RhoRoutes[Task] {
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
          absenceQueue.add(List(draft)) *> Ok("Added")
        }
      }
    }

  }
}
