package com.github.vsuharnikov.barbarissa.backend.employee

import doobie.util.{Get, Put}
import io.circe.{Decoder, Encoder}

case class EmployeeId(asString: String)
object EmployeeId {
  implicit val employeeIdGet: Get[EmployeeId] = Get[String].tmap(EmployeeId(_))
  implicit val employeeIdPut: Put[EmployeeId] = Put[String].contramap(_.asString)

  implicit val employeeIdEncoder: Encoder[EmployeeId] = Encoder[String].contramap(_.asString)
  implicit val employeeIdDecoder: Decoder[EmployeeId] = Decoder[String].map(EmployeeId(_))
}
