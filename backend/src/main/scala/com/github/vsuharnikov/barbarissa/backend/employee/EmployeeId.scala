package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.{Decoder, Encoder}

case class EmployeeId(asString: String)
object EmployeeId {
  implicit val employeeIdEncoder: Encoder[EmployeeId] = Encoder[String].contramap(_.asString)
  implicit val employeeIdDecoder: Decoder[EmployeeId] = Decoder[String].map(EmployeeId(_))
}
