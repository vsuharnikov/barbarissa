package com.github.vsuharnikov.barbarissa.backend.employee.app

import java.time.LocalDate

import io.circe.generic.JsonCodec

@JsonCodec case class HttpV0AbsenceAppointment(
    subject: String,
    description: String,
    startDate: LocalDate,
    endDate: LocalDate,
    serviceMark: String
)
