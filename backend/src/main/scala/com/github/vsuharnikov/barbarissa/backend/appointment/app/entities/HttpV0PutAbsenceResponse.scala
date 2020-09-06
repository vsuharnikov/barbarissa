package com.github.vsuharnikov.barbarissa.backend.appointment.app.entities

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec sealed trait HttpV0PutAbsenceResponse
object HttpV0PutAbsenceResponse {
  case class New(inner: HttpV0AbsenceAppointment) extends HttpV0PutAbsenceResponse
  object New {
    implicit val newEncoder: Encoder[New] = implicitly[Encoder[HttpV0PutAbsenceResponse]].contramap(x => x)
    implicit val newDecoder: Decoder[New] = implicitly[Decoder[HttpV0PutAbsenceResponse]].map {
      case x: New => x
      case x      => throw new RuntimeException(s"Expected New, but got $x")
    }
  }

  case object Created extends HttpV0PutAbsenceResponse {
    implicit val createdEncoder: Encoder[Created.type] = implicitly[Encoder[HttpV0PutAbsenceResponse]].contramap(x => x)
    implicit val createdDecoder: Decoder[Created.type] = implicitly[Decoder[HttpV0PutAbsenceResponse]].map {
      case x: Created.type => x
      case x               => throw new RuntimeException(s"Expected Created, but got $x")
    }
  }
}
