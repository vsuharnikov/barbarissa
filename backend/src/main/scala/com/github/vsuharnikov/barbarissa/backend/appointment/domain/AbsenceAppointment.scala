package com.github.vsuharnikov.barbarissa.backend.appointment.domain

import java.time.LocalDate

case class AbsenceAppointment(
    subject: String,
    description: String,
    startDate: LocalDate,
    endDate: LocalDate,
    serviceMark: String
)
