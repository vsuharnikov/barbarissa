package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId, EmployeeId}

case class Absence(absenceId: AbsenceId, employeeId: EmployeeId, from: LocalDate, daysQuantity: Int, reasonId: AbsenceReasonId)
