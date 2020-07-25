package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId
import io.estatico.newtype.macros.newtype

case class Absence(id: AbsenceId, from: LocalDate, daysQuantity: Int, reason: Absence.Reason)
object Absence {
  @newtype case class ReasonId(asString: String)
  case class Reason(id: ReasonId, name: String)
}
