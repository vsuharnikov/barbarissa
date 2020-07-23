package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

case class AbsenceAppointment(
    subject: String,
    description: String,
    startDate: LocalDate,
    endDate: LocalDate,
    serviceMark: String
)
