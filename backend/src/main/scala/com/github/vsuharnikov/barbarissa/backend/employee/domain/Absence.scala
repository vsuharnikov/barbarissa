package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId

case class Absence(id: AbsenceId, from: LocalDate, daysQuantity: Int, reason: String)
