package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId

case class AbsenceReason(id: AbsenceReasonId, name: String, needClaim: Option[AbsenceClaimType], needAppointment: Option[Boolean])
