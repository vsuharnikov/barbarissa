package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.AbsenceId

case class AbsenceQueueItem(absenceId: AbsenceId, done: Boolean, claimSent: Boolean, appointmentCreated: Boolean, retries: Int)
