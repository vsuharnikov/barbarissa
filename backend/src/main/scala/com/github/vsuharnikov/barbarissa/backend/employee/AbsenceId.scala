package com.github.vsuharnikov.barbarissa.backend.employee

import doobie.util.{Get, Put}

case class AbsenceId(asString: String)
object AbsenceId {
  implicit val unprocessedAbsenceGet: Get[AbsenceId] = Get[String].tmap(AbsenceId(_))
  implicit val unprocessedAbsencePut: Put[AbsenceId] = Put[String].contramap(_.asString)
}
