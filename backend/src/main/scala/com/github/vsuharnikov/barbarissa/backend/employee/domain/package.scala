package com.github.vsuharnikov.barbarissa.backend.employee

import io.circe.generic.extras.Configuration
import zio.Has

package object domain {
  type AbsenceRepo               = Has[AbsenceRepo.Service]
  type EmployeeRepo              = Has[EmployeeRepo.Service]
  type AbsenceReasonRepo         = Has[AbsenceReasonRepo.Service]
  type MigrationRepo             = Has[MigrationRepo.Service]
  type LastKnownAbsenceRepo      = Has[LastKnownAbsenceRepo.Service]
  type UnprocessedAbsenceRepo    = Has[UnprocessedAbsenceRepo.Service]
  type AbsenceAppointmentService = Has[AbsenceAppointmentService.Service]
  type ProcessingService         = Has[ProcessingService.Service]

  implicit val circeConfig: Configuration = Configuration.default
}
