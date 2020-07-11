package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.time.LocalDate

import io.circe.generic.JsonCodec

@JsonCodec case class HttpV0Absence(id: String, from: LocalDate, daysQuantity: Int, reason: String)
