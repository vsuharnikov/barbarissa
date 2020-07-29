package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId
import io.circe.generic.JsonCodec

@JsonCodec case class AbsenceQueueItem(absenceId: AbsenceId, done: Boolean, claimSent: Boolean, appointmentCreated: Boolean, retries: Int)
