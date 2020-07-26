package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.domain.VersionRepo
import doobie.implicits._
import io.github.gaelrenoux.tranzactio.doobie._
import io.github.gaelrenoux.tranzactio.{DbException, doobie => _}
import zio.{Has, Task, ZIO, ZLayer}

object DbVersionRepo {
  private def updateVersionSql(module: String, draftVersion: Int) =
    sql"""INSERT INTO LastMigration(module, version) VALUES ($module, $draftVersion)"""

  private val migrations = List(
    sql"""CREATE TABLE LastMigration(
module VARCHAR(100) PRIMARY KEY,
version INTEGER NOT NULL
)""",
    updateVersionSql("migrations", 1)
  )

  private def lastVersionQuery(module: String) = sql"SELECT version FROM LastMigration WHERE module = $module".query[Int]

  private def updateVersionQuery(module: String, draftVersion: Int) = updateVersionSql(module, draftVersion).update

  val live: ZLayer[Database, DbException, Has[VersionRepo.Service]] = {
    def migrationsFromInclusive(version: Int): ZIO[Connection, DbException, Unit] =
      ZIO
        .foreach(migrations.drop(version)) { s =>
          tzio {
            s.update.run.map(_ => ())
          }
        }
        .unit

    val allMigrations: ZIO[Connection, DbException, Unit] = migrationsFromInclusive(0)

    val r: ZIO[Database, DbException, VersionRepo.Service] = ZIO.accessM[Has[Database.Service]] { db =>
      val database = db.get[Database.Service]
      for {
        lastVersion <- database.transactionOrDie {
          tzio[Int] {
            lastVersionQuery("migrations").unique
          }.catchAll { _ =>
            allMigrations *> ZIO.succeed(migrations.size)
          }
        }
        _ <- database.transactionOrDie {
          migrationsFromInclusive(lastVersion + 1)
        }
      } yield
        new VersionRepo.Service {
          override def getLastVersion(module: String): Task[Int] =
            database.transactionOrDie(tzio(lastVersionQuery(module).unique))

          override def updateLastVersion(module: String, newVersion: Int): Task[Unit] =
            database.transactionOrDie(tzio(updateVersionQuery(module, newVersion).run.map(_ => ())))
        }
    }

    r.toLayer
  }
}
