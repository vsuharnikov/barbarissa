package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.employee.{AbsenceId, AbsenceReasonId}

case class Absence(id: AbsenceId, from: LocalDate, daysQuantity: Int, reason: Absence.Reason)
object Absence {
  case class Reason(id: AbsenceReasonId, name: String)
}
