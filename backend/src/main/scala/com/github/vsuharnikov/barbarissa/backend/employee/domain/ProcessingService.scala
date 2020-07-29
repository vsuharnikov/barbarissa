package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceRepo.GetAfterCursor
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.ForwardError
import zio.macros.accessible
import zio.{Task, ZLayer}

@accessible
object ProcessingService {
  trait Service {
    def refreshUnprocessed: Task[Unit]
    def process: Task[Unit]
  }

  val live =
    ZLayer.fromServices[LastKnownAbsenceRepo.Service, AbsenceRepo.Service, AbsenceReasonRepo.Service, UnprocessedAbsenceRepo.Service, Service] {
      (lastKnownRepo, absenceRepo, reasonRepo, unprocessedRepo) =>
        new Service {
          override def refreshUnprocessed: Task[Unit] =
            lastKnownRepo.lastAbsence.flatMap(aid => paginatedLoop(GetAfterCursor(aid, 0, 10)))

          override def process: Task[Unit] = ???

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
                  unprocessedRepo.add(xs) *> lastKnownRepo.update(draftLastKnown) *> Task.foreach(nextCursor)(paginatedLoop)
                }
              }
            } yield ()
        }
    }

  def toUnprocessed(a: Absence, ar: AbsenceReason): UnprocessedAbsence = UnprocessedAbsence(
    absenceId = a.id,
    done = false,
    hasClaim = ar.needClaim.fold(true)(_ => false),
    hasAppointment = ar.needAppointment.fold(true)(!_),
    retries = 0
  )
}
