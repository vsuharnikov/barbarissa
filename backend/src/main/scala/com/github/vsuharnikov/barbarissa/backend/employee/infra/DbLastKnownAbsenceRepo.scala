package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.AbsenceId
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{LastKnownAbsenceRepo, MigrationRepo}
import doobie.implicits._
import io.github.gaelrenoux.tranzactio.doobie.{Database, tzio}
import io.github.gaelrenoux.tranzactio.{doobie => _}
import zio.{Task, ZLayer}

object DbLastKnownAbsenceRepo {
  private val migrations = List(
    sql"""CREATE TABLE LastKnownAbsence(
id INTEGER NOT NULL PRIMARY KEY CHECK (id = 0),
lastAbsenceId VARCHAR(100) NOT NULL
)"""
  )

  val live = ZLayer.fromServicesM[MigrationRepo.Service, Database.Service, Any, Throwable, LastKnownAbsenceRepo.Service] { (migrateRepo, database) =>
    migrateRepo
      .migrate("DbLastKnownAbsence", migrations)
      .as(new LastKnownAbsenceRepo.Service {
        private val lastAbsenceQuery = sql"SELECT lastAbsenceId FROM LastKnownAbsence WHERE id = 0".query[String]
        override def lastAbsence: Task[Option[AbsenceId]] = database.transactionOrDie {
          tzio(lastAbsenceQuery.option).map(_.map(AbsenceId(_)))
        }

        private def updateQuery(draft: String) = sql"INSERT OR REPLACE INTO LastKnownAbsence (id, lastAbsenceId) VALUES (0, $draft)".update
        override def update(draft: AbsenceId): Task[Unit] = database.transactionOrDie {
          tzio(updateQuery(draft.asString).run).unit
        }
      })
  }
}
