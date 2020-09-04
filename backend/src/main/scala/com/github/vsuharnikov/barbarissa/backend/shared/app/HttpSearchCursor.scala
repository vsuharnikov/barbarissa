package com.github.vsuharnikov.barbarissa.backend.shared.app

import java.nio.charset.StandardCharsets
import java.util.Base64

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import sttp.tapir.{Schema, SchemaType}

case class HttpSearchCursor(startAt: Int, maxResults: Int)
object HttpSearchCursor {
  private val innerEncoder = deriveEncoder[HttpSearchCursor]
  private val innerDecoder = deriveDecoder[HttpSearchCursor]

  implicit val httpSearchCursorEncoder: Encoder[HttpSearchCursor] = Encoder.encodeString.contramap { x =>
    Base64.getEncoder.encodeToString(innerEncoder(x).noSpaces.getBytes(StandardCharsets.UTF_8))
  }

  // TODO
  implicit val httpSearchCursorDecoder: Decoder[HttpSearchCursor] = Decoder.decodeString.map { x =>
    val rawJson = new String(Base64.getDecoder.decode(x), StandardCharsets.UTF_8)
    decode(rawJson)(innerDecoder).getOrElse(throw new RuntimeException("Can't decode HttpSearchCursor"))
  }

  implicit val httpSearchCursorSchema: Schema[HttpSearchCursor] = Schema(SchemaType.SString).description("Base64-encoded string")
}
