package com.github.vsuharnikov.barbarissa.backend.employee

import com.github.vsuharnikov.barbarissa.backend.queue.domain.AbsenceQueue
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.MigrationRepo
import zio.Has

package object domain {
  type AbsenceQueue  = Has[AbsenceQueue.Service]
  type EmployeeRepo  = Has[EmployeeRepo.Service]
  type MigrationRepo = Has[MigrationRepo.Service]
}
