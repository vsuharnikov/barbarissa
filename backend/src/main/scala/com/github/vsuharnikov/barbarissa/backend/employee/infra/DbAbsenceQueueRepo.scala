package com.github.vsuharnikov.barbarissa.backend.employee.infra

import cats.instances.list._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.MigrationRepo.Migrations
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{AbsenceQueue, AbsenceQueueItem, MigrationRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.DbTransactor
import doobie.implicits._
import doobie.util.update.Update
import zio.interop.catz._
import zio.{Has, Task, ZIO, ZLayer}

object DbAbsenceQueueRepo {
  private val migrations: Migrations = List(
    sql"""CREATE TABLE AbsenceQueue(
absenceId VARCHAR(100) PRIMARY KEY NOT NULL,
done BOOLEAN NOT NULL,
claimSent BOOLEAN NOT NULL,
appointmentCreated BOOLEAN NOT NULL,
retries INT NOT NULL
)""",
    sql"""CREATE INDEX idx_AbsenceQueue_done ON AbsenceQueue (done)"""
  ).map(_.update.run.map(_ => ()))

  val live: ZLayer[DbTransactor with MigrationRepo, Throwable, Has[AbsenceQueue.Service]] = ZIO
    .accessM[DbTransactor with MigrationRepo] { env =>
      val tr          = env.get[DbTransactor.Service].transactor
      val migrateRepo = env.get[MigrationRepo.Service]

      migrateRepo
        .migrate("DbAbsenceQueueRepo", migrations)
        .as(new AbsenceQueue.Service {
          override def getUncompleted(num: Int): Task[List[AbsenceQueueItem]] = Sql.getUncompleted(num).transact(tr)
          override def add(drafts: List[AbsenceQueueItem]): Task[Unit]        = Sql.add.updateMany(drafts).transact(tr).unit
          override def update(draft: AbsenceQueueItem): Task[Unit]            = Sql.update.run(draft).transact(tr).unit
          override val last: Task[Option[AbsenceQueueItem]]                   = Sql.last.option.transact(tr)
        })
    }
    .toLayer

  object Sql {
    def getUncompleted(num: Int) = sql"""SELECT
absenceId, done, claimSent, appointmentCreated, retries
FROM AbsenceQueue WHERE done = FALSE ORDER BY absenceId LIMIT $num
""".query[AbsenceQueueItem].to[List]

    val add = Update[AbsenceQueueItem]("""INSERT OR IGNORE INTO
AbsenceQueue(absenceId, done, claimSent, appointmentCreated, retries)
VALUES(?, ?, ?, ?, ?)
""")

    val update = Update[AbsenceQueueItem]("""INSERT OR REPLACE INTO
AbsenceQueue(absenceId, done, claimSent, appointmentCreated, retries)
VALUES(?, ?, ?, ?, ?)
""")

    val last = sql"""SELECT
absenceId, done, claimSent, appointmentCreated, retries
FROM AbsenceQueue WHERE done = TRUE ORDER BY absenceId DESC LIMIT 1
""".query[AbsenceQueueItem]
  }
}
