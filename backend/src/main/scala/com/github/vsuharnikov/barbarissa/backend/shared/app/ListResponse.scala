package com.github.vsuharnikov.barbarissa.backend.shared.app

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ListResponse[T](items: List[T], nextCursor: Option[HttpSearchCursor])
object ListResponse {
  implicit def listResponseEncoder[T: Encoder]: Encoder[ListResponse[T]] = deriveEncoder
  implicit def listResponseDecoder[T: Decoder]: Decoder[ListResponse[T]] = deriveDecoder
}
