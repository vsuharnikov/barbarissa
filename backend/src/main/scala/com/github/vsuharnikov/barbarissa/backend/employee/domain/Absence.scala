package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId, EmployeeId}

case class Absence(id: AbsenceId, employeeId: EmployeeId, from: LocalDate, daysQuantity: Int, reason: Absence.Reason)
object Absence {
  case class Reason(id: AbsenceReasonId, name: String)
}
