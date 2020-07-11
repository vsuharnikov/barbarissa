package com.github.vsuharnikov.barbarissa.backend.shared.app

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

trait JsonSupport[F[_]] {
  implicit def circeJsonDecoder[A: Decoder](implicit sync: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A]
  implicit def circeJsonEncoder[A: Encoder]: EntityEncoder[F, A]                         = jsonEncoderOf[F, A]

  implicit def byteArrayEncoder: EntityEncoder[F, Array[Byte]] = EntityEncoder.byteArrayEncoder[F]
}
