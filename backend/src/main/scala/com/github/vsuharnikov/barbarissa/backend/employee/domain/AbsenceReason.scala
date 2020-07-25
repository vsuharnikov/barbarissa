package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceReasonId

case class AbsenceReason(id: AbsenceReasonId, claim: Option[AbsenceClaimType] = None, needAppointment: Option[Boolean] = None)
