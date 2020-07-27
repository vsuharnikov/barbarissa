package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.domain.{MigrationRepo, UnprocessedAbsence, UnprocessedAbsenceRepo}
import doobie.implicits._
import io.github.gaelrenoux.tranzactio.doobie.{Database, tzio}
import io.github.gaelrenoux.tranzactio.{doobie => _}
import zio.{Task, ZLayer}

object DbUnprocessedAbsenceRepo {
  private val migrations = List(
    sql"""CREATE TABLE UnprocessedAbsence(
absenceId VARCHAR(100) PRIMARY KEY NOT NULL,
done BOOLEAN NOT NULL,
hasRequiredClaim BOOLEAN NOT NULL,
hasAppointment BOOLEAN NOT NULL,
retries INT NOT NULL
);
CREATE INDEX idx_UnprocessedAbsence_done ON UnprocessedAbsence (done);"""
  )

  val live = ZLayer.fromServicesM[MigrationRepo.Service, Database.Service, Any, Throwable, UnprocessedAbsenceRepo.Service] {
    (migrateRepo, database) =>
      migrateRepo
        .migrate("DbUnprocessedAbsenceRepo", migrations)
        .as(new UnprocessedAbsenceRepo.Service {
          override def get(num: Int): Task[List[UnprocessedAbsence]] = database.transactionOrDie {
            tzio(sql"""SELECT 
absenceId, done, hasRequiredClaim, hasRequiredClaim, hasAppointment, retries
FROM LastKnownAbsence WHERE done = TRUE ORDER BY absenceId LIMIT $num
""".query[UnprocessedAbsence].to[List])
          }

          override def update(draft: UnprocessedAbsence): Task[Unit] = database.transactionOrDie {
            import draft._
            tzio(sql"""INSERT OR UPDATE INTO
UnprocessedAbsence(absenceId, done, hasRequiredClaim, hasAppointment, retries)
VALUES($absenceId, $done, $hasRequiredClaim, $hasAppointment, $retries)
""".update.run).unit
          }
        })
  }
}
