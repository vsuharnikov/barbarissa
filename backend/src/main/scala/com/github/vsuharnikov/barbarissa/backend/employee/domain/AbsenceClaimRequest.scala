package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs

// sinGen - singular genitive
case class AbsenceClaimRequest(sinGenPosition: String, sinGenFullName: String, sinGenFromDate: String, daysQuantity: Int, reportDate: String)

object AbsenceClaimRequest {
  implicit val absenceClaimRequestToArgs: ToArgs[AbsenceClaimRequest] = ToArgs.gen[AbsenceClaimRequest]
}
