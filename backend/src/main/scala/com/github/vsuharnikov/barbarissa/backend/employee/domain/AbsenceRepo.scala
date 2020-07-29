package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error
import io.circe.generic.JsonCodec
import zio.ZIO

// @accessible see https://github.com/zio/zio/issues/4020
object AbsenceRepo {
  type RepoIO[A]                 = ZIO[Any, error.RepoError, A]
  type RepoMultipleIO[A, Cursor] = RepoIO[(List[A], Option[Cursor])]

  trait Service {
    // TODO ZStream? Probably, if we know what we need to do with the end
    def getById(id: EmployeeId): RepoMultipleIO[Absence, GetCursor] = getByCursor(GetCursor(id, 0, 10))
    def getByCursor(cursor: GetCursor): RepoMultipleIO[Absence, GetCursor]

    def get(by: EmployeeId, absenceId: AbsenceId): RepoIO[Absence]

    def getFromById(id: Option[AbsenceId]): RepoMultipleIO[Absence, GetAfterCursor] = getFromByCursor(GetAfterCursor(id, 0, 10))
    def getFromByCursor(cursor: GetAfterCursor): RepoMultipleIO[Absence, GetAfterCursor]
  }

  def getById(id: EmployeeId)        = ZIO.accessM[AbsenceRepo](_.get[Service].getById(id))
  def getByCursor(cursor: GetCursor) = ZIO.accessM[AbsenceRepo](_.get[Service].getByCursor(cursor))

  def get(by: EmployeeId, absenceId: AbsenceId) = ZIO.accessM[AbsenceRepo](_.get[Service].get(by, absenceId))

  def getFromById(id: Option[AbsenceId])      = ZIO.accessM[AbsenceRepo](_.get[Service].getFromById(id))
  def getFromByCursor(cursor: GetAfterCursor) = ZIO.accessM[AbsenceRepo](_.get[Service].getFromByCursor(cursor))

  @JsonCodec case class GetCursor(by: EmployeeId, startAt: Int, maxResults: Int)
  @JsonCodec case class GetAfterCursor(from: Option[AbsenceId], startAt: Int, maxResults: Int)
}
