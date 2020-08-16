package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, EmployeeId}
import zio.{Task, ZIO}

// @accessible see https://github.com/zio/zio/issues/4020
object AbsenceRepo extends Serializable {
  type RepoMultipleIO[A, Cursor] = Task[(List[A], Option[Cursor])]

  // TODO ZStream? Probably, if we know what we need to do with the end
  trait Service extends Serializable {
    def get(absenceId: AbsenceId): Task[Option[Absence]]

    def getById(id: EmployeeId): RepoMultipleIO[Absence, GetCursor] = getByCursor(GetCursor(id, 0, 10))
    def getByCursor(cursor: GetCursor): RepoMultipleIO[Absence, GetCursor]

    def getFromById(id: Option[AbsenceId]): RepoMultipleIO[Absence, GetAfterCursor] = getFromByCursor(GetAfterCursor(id, 0, 10))
    def getFromByCursor(cursor: GetAfterCursor): RepoMultipleIO[Absence, GetAfterCursor]
  }

  def getById(id: EmployeeId)        = ZIO.accessM[AbsenceRepo](_.get[Service].getById(id))
  def getByCursor(cursor: GetCursor) = ZIO.accessM[AbsenceRepo](_.get[Service].getByCursor(cursor))

  def get(absenceId: AbsenceId) = ZIO.accessM[AbsenceRepo](_.get[Service].get(absenceId))

  def getFromById(id: Option[AbsenceId])      = ZIO.accessM[AbsenceRepo](_.get[Service].getFromById(id))
  def getFromByCursor(cursor: GetAfterCursor) = ZIO.accessM[AbsenceRepo](_.get[Service].getFromByCursor(cursor))

  case class GetCursor(by: EmployeeId, startAt: Int, maxResults: Int)
  case class GetAfterCursor(from: Option[AbsenceId], startAt: Int, maxResults: Int)
}
