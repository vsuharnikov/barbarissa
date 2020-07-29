package com.github.vsuharnikov.barbarissa.backend.employee

import doobie.util.{Get, Put}
import io.circe.{Decoder, Encoder}

case class AbsenceId(asString: String)
object AbsenceId {
  implicit val unprocessedAbsenceGet: Get[AbsenceId] = Get[String].tmap(AbsenceId(_))
  implicit val unprocessedAbsencePut: Put[AbsenceId] = Put[String].contramap(_.asString)

  implicit val absenceIdEncoder: Encoder[AbsenceId] = Encoder[String].contramap(_.asString)
  implicit val absenceIdDecoder: Decoder[AbsenceId] = Decoder[String].map(AbsenceId(_))
}
