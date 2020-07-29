package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId

case class AbsenceReason(id: AbsenceReasonId, needClaim: Option[AbsenceClaimType], needAppointment: Option[Boolean])
