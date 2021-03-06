package com.github.vsuharnikov.barbarissa.backend.absence.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceReasonId

case class AbsenceReason(id: AbsenceReasonId, name: String, needClaim: Option[AbsenceClaimType], needAppointment: Option[Boolean])
