package com.github.vsuharnikov.barbarissa.backend.employee.app.entities

import java.time.LocalDate

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0AbsenceAppointment(
    subject: String,
    description: String,
    startDate: LocalDate,
    endDate: LocalDate,
    serviceMark: String
)
