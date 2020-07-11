package com.github.vsuharnikov.barbarissa.backend.employee.domain

import java.time.LocalDate

import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs

case class AbsenceClaimRequest(singularGenitivePosition: String,
                               singularGenitiveFullName: String,
                               from: LocalDate,
                               daysQuantity: Int,
                               reportDate: LocalDate)

object AbsenceClaimRequest {
  implicit val absenceClaimRequestToArgs: ToArgs[AbsenceClaimRequest] = ToArgs.gen[AbsenceClaimRequest]
}
