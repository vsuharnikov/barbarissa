package com.github.vsuharnikov.barbarissa.backend.shared.domain

import io.circe.generic.JsonCodec

@JsonCodec case class MultipleResultsCursor(startAt: Int, maxResults: Int)
