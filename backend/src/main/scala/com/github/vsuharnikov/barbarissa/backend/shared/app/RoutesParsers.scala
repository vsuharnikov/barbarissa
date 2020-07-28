package com.github.vsuharnikov.barbarissa.backend.shared.app

import java.util.Base64

import cats.Applicative
import cats.syntax.applicative._
import com.github.vsuharnikov.barbarissa.backend.shared.domain.MultipleResultsCursor
import org.http4s.rho.bits.{FailureResponse, ResponseReason, StringParser, SuccessResponse}
import org.http4s.{Response, Status}

import scala.util.Try

object RoutesParsers {
  implicit def multipleResultsCursorParser[F[_]: Applicative]: StringParser[F, MultipleResultsCursor] = StringParser.strParser[F].rmap { x =>
    Try(Base64.getDecoder.decode(x)).toOption match {
      case None => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F])) // TODO
      case Some(bytes) =>
        io.circe.jawn.parseByteArray(bytes) match {
          case Left(_) => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F])) // TODO
          case Right(x) =>
            x.as[MultipleResultsCursor] match {
              case Left(_)  => FailureResponse[F](new ResponseReason[F](Response[F](status = Status.BadRequest).pure[F]))
              case Right(x) => SuccessResponse(x)
            }
        }
    }
  }
}
