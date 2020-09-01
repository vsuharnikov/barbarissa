package com.github.vsuharnikov.barbarissa.backend.shared.app

import java.util.Base64

import cats.Applicative
import cats.syntax.applicative._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, EmployeeId}
import org.http4s.rho.bits.{FailureResponse, ResponseReason, StringParser, SuccessResponse}
import org.http4s.{Response, Status}

import scala.util.Try

class RoutesParsers[F[_]: Applicative] {
  implicit val httpSearchCursorSP: StringParser[F, HttpSearchCursor] = StringParser.strParser[F].rmap { x =>
    Try(Base64.getDecoder.decode(x)).toOption match {
      case None => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F])) // TODO
      case Some(bytes) =>
        io.circe.jawn.parseByteArray(bytes) match {
          case Left(_) => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F])) // TODO
          case Right(x) =>
            x.as[HttpSearchCursor] match {
              case Left(_)  => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F]))
              case Right(x) => SuccessResponse(x)
            }
        }
    }
  }

  implicit val employeeIdSP: StringParser[F, EmployeeId] = StringParser.strParser[F].map(EmployeeId)
  implicit val absenceIdSP: StringParser[F, AbsenceId]   = StringParser.strParser[F].map(AbsenceId(_))
}
