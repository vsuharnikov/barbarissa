package com.github.vsuharnikov.barbarissa.backend.shared.domain.error

import com.github.vsuharnikov.barbarissa.backend.shared.domain.error

case class ForwardError(x: error.RepoError) extends RuntimeException(s"Error: $x", null, true, false)
