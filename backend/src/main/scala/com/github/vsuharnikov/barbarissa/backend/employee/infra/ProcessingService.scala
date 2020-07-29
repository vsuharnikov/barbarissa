package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceRepo.GetAfterCursor
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.ReportService
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import zio.macros.accessible
import zio.{Task, ZLayer}

@accessible
object ProcessingService {
  trait Service {
    def refreshUnprocessed: Task[Unit]
    def process: Task[Unit]
    def processMany(xs: List[AbsenceQueueItem]): Task[Unit]
    def processOne(x: AbsenceQueueItem): Task[Unit]
  }

  type Dependencies = AbsenceRepo
    with AbsenceReasonRepo
    with AbsenceQueueRepo
    with AbsenceAppointmentService
    with ReportService

  val live = ZLayer.fromFunction[Dependencies, Service] { env =>
    val absenceRepo               = env.get[AbsenceRepo.Service]
    val reasonRepo                = env.get[AbsenceReasonRepo.Service]
    val queueRepo           = env.get[AbsenceQueueRepo.Service]
    val absenceAppointmentService = env.get[AbsenceAppointmentService.Service]
    val reportService             = env.get[ReportService.Service]

    new Service {
      override def refreshUnprocessed: Task[Unit] =
        queueRepo.last.flatMap(x => paginatedLoop(GetAfterCursor(x.map(_.absenceId), 0, 10)))

      override def process: Task[Unit] =
        queueRepo.get(10).flatMap { xs =>
          if (xs.isEmpty) Task.unit
          else processMany(xs) *> process
        }

      override def processMany(xs: List[AbsenceQueueItem]): Task[Unit] = Task.foreach(xs)(processOne).unit

      override def processOne(x: AbsenceQueueItem): Task[Unit] = Task.unit // TODO

      // TODO ZStream
      private def paginatedLoop(cursor: GetAfterCursor): Task[Unit] =
        for {
          r          <- absenceRepo.getFromByCursor(cursor).mapError(ForwardError) // TODO
          reasonsMap <- reasonRepo.all.mapError(ForwardError)
          _ <- {
            val (unprocessed, nextCursor) = r
            val xs = unprocessed.view.map { a =>
              toUnprocessed(a, reasonsMap(a.reason.id)) // TODO
            }.toList

            if (xs.isEmpty) Task.unit
            else {
              val draftLastKnown = unprocessed.last.id
              queueRepo.add(xs) *> Task.foreach(nextCursor)(paginatedLoop)
            }
          }
        } yield ()
    }
  }

  def toUnprocessed(a: Absence, ar: AbsenceReason): AbsenceQueueItem = AbsenceQueueItem(
    absenceId = a.id,
    done = false,
    claimSent = ar.needClaim.fold(true)(_ => false),
    appointmentCreated = ar.needAppointment.fold(true)(!_),
    retries = 0
  )
}
