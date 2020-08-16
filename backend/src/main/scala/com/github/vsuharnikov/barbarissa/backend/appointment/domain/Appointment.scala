package com.github.vsuharnikov.barbarissa.backend.appointment.domain

import java.time.LocalDate

case class Appointment(
    subject: String,
    description: String,
    startDate: LocalDate,
    endDate: LocalDate,
    serviceMark: String
)
