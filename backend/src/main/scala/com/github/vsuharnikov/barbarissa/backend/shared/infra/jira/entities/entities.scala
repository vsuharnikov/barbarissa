package com.github.vsuharnikov.barbarissa.backend.shared.infra.jira

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import cats.syntax.either._
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder}

package object entities {
  implicit val circeConfig: Configuration = Configuration.default

  // TODO Move
  // E.g.: 2020-09-02T05:56:35.000+0000
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  implicit val localDateTimeEncoder = Encoder.encodeString.contramap[LocalDateTime](_.format(formatter))
  implicit val localDateTimeDecoder = Decoder.decodeString.emap[LocalDateTime] { str =>
    Either.catchNonFatal(LocalDateTime.parse(str, formatter)).leftMap(_.getMessage)
  }
}
