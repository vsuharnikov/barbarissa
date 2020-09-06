package com.github.vsuharnikov.barbarissa.backend.queue.app

import com.github.vsuharnikov.barbarissa.backend.employee.app.entities._
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceId
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.ztapir._
import zio.ZLayer
import zio.interop.catz._
import zio.macros.accessible

@accessible
object QueueHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromService[AbsenceQueue.Service, Service] { absenceQueue =>
    new Service with TapirCommonEntities {
      val add = endpoint.post
        .in("api" / "v0" / "queue" / "add")
        .in(jsonBody[HttpV0AbsenceQueueItem])
        .out(jsonBody[HttpMessage])
        .errorOut(errorOut)
        .tag("queue")
        .description("Add an item to the queue")

      val addRoute = add.toRoutes { api =>
        val draft = AbsenceQueueItem(
          absenceId = AbsenceId(api.absenceId),
          done = api.done,
          claimSent = api.done,
          appointmentCreated = api.appointmentCreated,
          retries = api.retries
        )
        absenceQueue.add(List(draft)).as(HttpMessage("Added"))
      }

      override val openApiDoc   = List(add).toOpenAPI("", "")
      override val http4sRoutes = List(addRoute)
    }

  }
}
