package com.github.vsuharnikov.barbarissa.backend.shared.domain.error

import org.http4s.Status

case class ForwardError(x: DomainError) extends RuntimeException(s"Error: $x", null, true, false)
class HttpServerException(status: Status) extends RuntimeException(s"Server error: $status")
class HttpClientException(status: Status) extends RuntimeException(s"Client error: $status")
