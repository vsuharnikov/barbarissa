package com.github.vsuharnikov.barbarissa.backend.absence.app.entities

import java.time.LocalDate

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0Absence(id: String, from: LocalDate, daysQuantity: Int, reason: String)
