package com.github.vsuharnikov.barbarissa.backend.employee.app

import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec case class HttpV0AbsenceQueueItem(
    absenceId: String,
    done: Boolean = false,
    claimSent: Boolean = false,
    appointmentCreated: Boolean = false,
    retries: Int = 0
)
