package com.github.vsuharnikov.barbarissa.backend.employee.infra

import cats.instances.list._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{MigrationRepo, AbsenceQueueItem, AbsenceQueueRepo}
import doobie.implicits._
import doobie.util.update.Update
import io.github.gaelrenoux.tranzactio.doobie.{Database, tzio}
import io.github.gaelrenoux.tranzactio.{doobie => _}
import zio.{Task, ZLayer}

object DbAbsenceQueueRepo {
  private val migrations = List(
    sql"""CREATE TABLE AbsenceQueue(
absenceId VARCHAR(100) PRIMARY KEY NOT NULL,
done BOOLEAN NOT NULL,
claimSent BOOLEAN NOT NULL,
appointmentCreated BOOLEAN NOT NULL,
retries INT NOT NULL
);
CREATE INDEX idx_AbsenceQueue_done ON AbsenceQueue (done);"""
  )

  val live = ZLayer.fromServicesM[MigrationRepo.Service, Database.Service, Any, Throwable, AbsenceQueueRepo.Service] {
    (migrateRepo, database) =>
      migrateRepo
        .migrate("DbAbsenceQueueRepo", migrations)
        .as(new AbsenceQueueRepo.Service {
          override def get(num: Int): Task[List[AbsenceQueueItem]] = database.transactionOrDie {
            tzio(sql"""SELECT 
absenceId, done, claimSent, appointmentCreated, retries
FROM LastKnownAbsence WHERE done = TRUE ORDER BY absenceId LIMIT $num
""".query[AbsenceQueueItem].to[List])
          }

          private val addOneQuery = Update[AbsenceQueueItem]("""INSERT OR IGNORE INTO
AbsenceQueue(absenceId, done, claimSent, appointmentCreated, retries)
VALUES(?, ?, ?, ?, ?)
""")
          override def add(drafts: List[AbsenceQueueItem]): Task[Unit] = database.transactionOrDie {
            tzio(addOneQuery.updateMany(drafts)).unit
          }

          override def update(draft: AbsenceQueueItem): Task[Unit] = database.transactionOrDie {
            import draft._
            tzio(sql"""INSERT OR REPLACE INTO
AbsenceQueue(absenceId, done, claimSent, appointmentCreated, retries)
VALUES($absenceId, $done, $claimSent, $appointmentCreated, $retries)
""".update.run).unit
          }

          override def last: Task[Option[AbsenceQueueItem]] = database.transactionOrDie {
            tzio(sql"""SELECT
absenceId, done, claimSent, appointmentCreated, retries
FROM LastKnownAbsence WHERE done = TRUE ORDER BY absenceId DESC LIMIT 1
""".query[AbsenceQueueItem].option)
          }
        })
  }
}
