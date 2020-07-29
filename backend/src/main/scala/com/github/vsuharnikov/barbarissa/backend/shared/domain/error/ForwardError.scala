package com.github.vsuharnikov.barbarissa.backend.shared.domain.error

case class ForwardError(x: DomainError) extends RuntimeException(s"Error: $x", null, true, false)
