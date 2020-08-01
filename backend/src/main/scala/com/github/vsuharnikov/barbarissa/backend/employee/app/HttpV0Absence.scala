package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.time.LocalDate

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0Absence(id: String, from: LocalDate, daysQuantity: Int, reason: String)
