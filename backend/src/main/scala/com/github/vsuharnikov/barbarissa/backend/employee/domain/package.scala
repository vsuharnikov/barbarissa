package com.github.vsuharnikov.barbarissa.backend.employee

import zio.Has

package object domain {
  type AbsenceRepo               = Has[AbsenceRepo.Service]
  type EmployeeRepo              = Has[EmployeeRepo.Service]
  type AbsenceAppointmentService = Has[AbsenceAppointmentService.Service]
}
