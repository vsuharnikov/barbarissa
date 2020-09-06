package com.github.vsuharnikov.barbarissa.backend.shared.app

import cats.effect.Sync
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, DomainError, EmployeeId}
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

trait JsonEntitiesEncoding[F[_]] {
  implicit def circeJsonDecoder[A: Decoder](implicit sync: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A]
  implicit def circeJsonEncoder[A: Encoder]: EntityEncoder[F, A]                         = jsonEncoderOf[F, A]

  implicit def byteArrayEncoder: EntityEncoder[F, Array[Byte]] = EntityEncoder.byteArrayEncoder[F]

  implicit val absenceIdEncoder: Encoder[AbsenceId] = Encoder[String].contramap(_.asString)
  implicit val absenceIdDecoder: Decoder[AbsenceId] = Decoder[String].map(AbsenceId(_))

  implicit val employeeIdEncoder: Encoder[EmployeeId] = Encoder[String].contramap(_.asString)
  implicit val employeeIdDecoder: Decoder[EmployeeId] = Decoder[String].map(EmployeeId)

  implicit val errorDecoder: Decoder[Throwable] = Decoder[ApiError.Json].map[Throwable] { x =>
    new RuntimeException(s"${x.name}: ${x.message}")
  }

  implicit val errorEncoder: Encoder[Throwable] = Encoder[ApiError.Json].contramap[Throwable] { x =>
    // WARN: JRE 9+ https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8057919
    ApiError.Json(x.getClass.getSimpleName, Option(x.getMessage).getOrElse("<null>"))
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
}
