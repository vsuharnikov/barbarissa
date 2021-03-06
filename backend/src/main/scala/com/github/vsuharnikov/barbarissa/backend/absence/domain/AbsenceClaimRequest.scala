package com.github.vsuharnikov.barbarissa.backend.absence.domain

import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs

// sinGen - singular genitive
case class AbsenceClaimRequest(
    sinGenPosition: String,
    sinGenFullName: String,
    sinGenFromDate: String,
    plurDaysQuantity: String,
    reportDate: String
)

object AbsenceClaimRequest {
  implicit val absenceClaimRequestToArgs: ToArgs[AbsenceClaimRequest] = ToArgs.gen[AbsenceClaimRequest]
}
