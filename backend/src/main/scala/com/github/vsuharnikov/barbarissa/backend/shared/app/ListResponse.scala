package com.github.vsuharnikov.barbarissa.backend.shared.app

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.github.vsuharnikov.barbarissa.backend.shared.domain.MultipleResultsCursor
import io.circe.syntax._
import io.circe.{Encoder, Json}

case class ListResponse[T](items: List[T], nextCursor: Option[MultipleResultsCursor])
object ListResponse {
  implicit def listResponseEncoder[T: Encoder]: Encoder[ListResponse[T]] =
    (a: ListResponse[T]) =>
      Json.obj(
        "items" -> Json.fromValues(a.items.map(_.asJson)),
        "nextCursor" -> a.nextCursor.map { x =>
          Base64.getEncoder.encodeToString(x.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
        }.asJson
    )
}
