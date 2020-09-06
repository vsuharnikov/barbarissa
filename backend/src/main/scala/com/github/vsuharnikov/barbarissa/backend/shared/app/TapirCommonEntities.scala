package com.github.vsuharnikov.barbarissa.backend.shared.app

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, DomainError, EmployeeId}
import io.circe.Encoder
import io.circe.parser.decode
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import zio.Task

trait TapirCommonEntities extends JsonEntitiesEncoding[Task] {
  implicit val employeeIdCodec: Codec[String, EmployeeId, CodecFormat.TextPlain] = Codec.string.map(EmployeeId)(_.asString)
  implicit val absenceIdCodec: Codec[String, AbsenceId, CodecFormat.TextPlain]   = Codec.string.map(AbsenceId(_))(_.asString)
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
  val absenceIdPath     = path[AbsenceId]("absenceId")
  val searchCursorQuery = query[Option[HttpSearchCursor]]("cursor")

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
