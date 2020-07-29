package com.github.vsuharnikov.barbarissa.backend.shared.app

import io.circe.generic.JsonCodec

@JsonCodec case class HttpSearchCursor(startAt: Int, maxResults: Int)
