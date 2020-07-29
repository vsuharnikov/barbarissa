package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId

case class UnprocessedAbsence(absenceId: AbsenceId, done: Boolean, hasClaim: Boolean, hasAppointment: Boolean, retries: Int)
