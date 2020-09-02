package com.github.vsuharnikov.barbarissa.backend.absence.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, AbsenceReasonId, EmployeeId}

case class Absence(absenceId: AbsenceId, employeeId: EmployeeId, from: LocalDate, daysQuantity: Int, reasonId: AbsenceReasonId, created: LocalDate)
