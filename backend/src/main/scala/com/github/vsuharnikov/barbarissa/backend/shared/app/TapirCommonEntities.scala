package com.github.vsuharnikov.barbarissa.backend.shared.app

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{DomainError, EmployeeId}
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

trait TapirCommonEntities {
  implicit val employeeIdCodec: Codec[String, EmployeeId, CodecFormat.TextPlain] = Codec.string.map(EmployeeId)(_.asString)
  implicit val searchCursorCodec: Codec[String, HttpSearchCursor, CodecFormat.TextPlain] = Codec.string
    .mapDecode { x =>
      decode[HttpSearchCursor](x)(HttpSearchCursor.httpSearchCursorDecoder) match {
        case Left(e)  => DecodeResult.Error(e.getMessage, e)
        case Right(x) => DecodeResult.Value(x)
      }
    } { x =>
      Encoder[HttpSearchCursor].apply(x).noSpaces
    }

  val employeeIdPath    = path[EmployeeId]("employeeId")
  val searchCursorQuery = query[Option[HttpSearchCursor]]("cursor")

  implicit val errorEncoder: Encoder[Throwable] = Encoder[ApiError.Json].contramap[Throwable] { x =>
    // WARN: JRE 9+ https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8057919
    ApiError.Json(x.getClass.getSimpleName, Option(x.getMessage).getOrElse("<null>"))
  }

  implicit val errorDecoder: Decoder[Throwable] = Decoder[ApiError.Json].map[Throwable] { x =>
    new RuntimeException(s"${x.name}: ${x.message}")
  }

  implicit val unhandledErrorEncoder: Encoder[DomainError.UnhandledError] = errorEncoder.contramap(x => x)
  implicit val unhandledErrorDecoder: Decoder[DomainError.UnhandledError] = errorDecoder.map {
    case x: DomainError.UnhandledError => x
  }

  implicit val notEnoughErrorEncoder: Encoder[DomainError.NotEnoughData] = errorEncoder.contramap(x => x)
  implicit val notEnoughErrorDecoder: Decoder[DomainError.NotEnoughData] = errorDecoder.map {
    case x: DomainError.NotEnoughData => x
  }

  implicit val configurationErrorEncoder: Encoder[DomainError.ConfigurationError] = errorEncoder.contramap(x => x)
  implicit val configurationErrorDecoder: Decoder[DomainError.ConfigurationError] = errorDecoder.map {
    case x: DomainError.ConfigurationError => x
  }

  implicit val remoteCallFailedEncoder: Encoder[DomainError.RemoteCallFailed] = errorEncoder.contramap(x => x)
  implicit val remoteCallFailedDecoder: Decoder[DomainError.RemoteCallFailed] = errorDecoder.map {
    case x: DomainError.RemoteCallFailed => x
  }

  implicit val notFoundEncoder: Encoder[DomainError.NotFound] = errorEncoder.contramap(x => x)
  implicit val notFoundDecoder: Decoder[DomainError.NotFound] = errorDecoder.map {
    case x: DomainError.NotFound => x
  }

  implicit val impossibleEncoder: Encoder[DomainError.Impossible] = errorEncoder.contramap(x => x)
  implicit val impossibleDecoder: Decoder[DomainError.Impossible] = errorDecoder.map {
    case x: DomainError.Impossible => x
  }

  implicit val jiraErrorEncoder: Encoder[DomainError.JiraError] = errorEncoder.contramap(x => x)
  implicit val jiraErrorDecoder: Decoder[DomainError.JiraError] = errorDecoder.map {
    case x: DomainError.JiraError => x
  }

  val errorOut = oneOf[Throwable](
    statusMapping(StatusCode.InternalServerError, jsonBody[DomainError.UnhandledError]),
    statusMapping(StatusCode.InternalServerError, jsonBody[DomainError.NotEnoughData]),
    statusMapping(StatusCode.InternalServerError, jsonBody[DomainError.ConfigurationError]),
    statusMapping(StatusCode.BadGateway, jsonBody[DomainError.RemoteCallFailed]),
    statusMapping(StatusCode.NotFound, jsonBody[DomainError.NotFound]),
    statusMapping(StatusCode.BadRequest, jsonBody[DomainError.Impossible]),
    statusMapping(StatusCode.InternalServerError, jsonBody[DomainError.JiraError])
  )
}
