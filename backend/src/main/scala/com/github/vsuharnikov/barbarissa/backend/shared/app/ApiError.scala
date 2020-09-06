package com.github.vsuharnikov.barbarissa.backend.shared.app

import com.github.vsuharnikov.barbarissa.backend.shared.domain.DomainError
import io.circe.Json
import io.circe.generic.JsonCodec
import org.http4s.Status

class ApiError(val status: Status, val name: String, override val getMessage: String) extends RuntimeException("", null, true, false) {
  def body = ApiError.Json(name, getMessage)
}

object ApiError {
  @JsonCodec case class Json(name: String, message: String)

  def from(x: DomainError)         = new ApiError(statusFrom(x), x.name, x.getMessage)
  def clientError(message: String) = new ApiError(Status.BadRequest, "ClientError", message)

  def statusFrom(x: DomainError): Status = x match {
    case _: DomainError.UnhandledError     => Status.InternalServerError
    case _: DomainError.NotEnoughData      => Status.InternalServerError
    case _: DomainError.ConfigurationError => Status.InternalServerError
    case _: DomainError.RemoteCallFailed   => Status.BadGateway
    case _: DomainError.NotFound           => Status.NotFound
    case _: DomainError.Impossible         => Status.BadRequest
    case _: DomainError.JiraError          => Status.InternalServerError
  }
}
