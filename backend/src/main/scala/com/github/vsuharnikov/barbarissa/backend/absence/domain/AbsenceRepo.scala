package com.github.vsuharnikov.barbarissa.backend.absence.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, DomainError, EmployeeId}
import zio.{Task, ZIO}
import zio.macros.accessible

@accessible
object AbsenceRepo extends Serializable {
  // TODO ZStream? Probably, if we know what we need to do with the end
  trait Service extends Serializable {
    // See https://github.com/zio/zio/issues/4099
    // type RepoMultipleIO[A, Cursor] = Task[(List[A], Option[Cursor])]

    def unsafeGet(absenceId: AbsenceId): Task[Absence] = get(absenceId).flatMap {
      case Some(x) => ZIO.succeed(x)
      case None    => ZIO.fail(DomainError.NotFound("Absence", absenceId.asString))
    }
    def get(absenceId: AbsenceId): Task[Option[Absence]]

    def getById(id: EmployeeId): Task[(List[Absence], Option[GetCursor])] = getByCursor(GetCursor(id, 0, 10))
    def getByCursor(cursor: GetCursor): Task[(List[Absence], Option[GetCursor])]

    def getFromById(id: Option[AbsenceId]): Task[(List[Absence], Option[GetAfterCursor])] = getFromByCursor(GetAfterCursor(id, 0, 10))
    def getFromByCursor(cursor: GetAfterCursor): Task[(List[Absence], Option[GetAfterCursor])]
  }

  case class GetCursor(by: EmployeeId, startAt: Int, maxResults: Int)
  case class GetAfterCursor(from: Option[AbsenceId], startAt: Int, maxResults: Int)
}
