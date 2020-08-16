package com.github.vsuharnikov.barbarissa.backend.employee.app.entities

import cats.effect.Sync
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, EmployeeId}
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

trait JsonEntitiesEncoding[F[_]] {
  implicit def circeJsonDecoder[A: Decoder](implicit sync: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A]
  implicit def circeJsonEncoder[A: Encoder]: EntityEncoder[F, A]                         = jsonEncoderOf[F, A]

  implicit def byteArrayEncoder: EntityEncoder[F, Array[Byte]] = EntityEncoder.byteArrayEncoder[F]

  implicit val absenceIdEncoder: Encoder[AbsenceId] = Encoder[String].contramap(_.asString)
  implicit val absenceIdDecoder: Decoder[AbsenceId] = Decoder[String].map(AbsenceId)

  implicit val employeeIdEncoder: Encoder[EmployeeId] = Encoder[String].contramap(_.asString)
  implicit val employeeIdDecoder: Decoder[EmployeeId] = Decoder[String].map(EmployeeId)
}
