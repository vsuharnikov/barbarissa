package com.github.vsuharnikov.barbarissa.backend.shared.app

import com.github.vsuharnikov.barbarissa.backend.shared.domain.DomainError
import io.circe.Json
import org.http4s.Status

class ApiError(val status: Status, val name: String, override val getMessage: String) extends RuntimeException("", null, true, false) {
  def body: Json = Json.obj(
    "name"    -> Json.fromString(name),
    "message" -> Json.fromString(getMessage)
  )
}

object ApiError {
  def from(x: DomainError)         = new ApiError(statusFrom(x), x.name, x.getMessage)
  def clientError(message: String) = new ApiError(Status.BadRequest, "ClientError", message)

  def statusFrom(x: DomainError): Status = x match {
    case _: DomainError.UnhandledError     => Status.InternalServerError
    case _: DomainError.ConfigurationError => Status.InternalServerError
    case _: DomainError.RemoteCallFailed   => Status.BadGateway
    case _: DomainError.NotFound           => Status.NotFound
  }
}
