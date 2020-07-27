package com.github.vsuharnikov.barbarissa.backend.employee

import zio.Has

package object domain {
  type AbsenceRepo               = Has[AbsenceRepo.Service]
  type EmployeeRepo              = Has[EmployeeRepo.Service]
  type AbsenceReasonRepo         = Has[AbsenceReasonRepo.Service]
  type MigrationRepo             = Has[MigrationRepo.Service]
  type LastKnownAbsenceRepo      = Has[LastKnownAbsenceRepo.Service]
  type LastKnownAbsence          = Has[LastKnownAbsence.Service]
  type UnprocessedAbsenceRepo    = Has[UnprocessedAbsenceRepo.Service]
  type AbsenceAppointmentService = Has[AbsenceAppointmentService.Service]
}
