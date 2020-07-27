package com.github.vsuharnikov.barbarissa.backend.employee.infra

import com.github.vsuharnikov.barbarissa.backend.employee.domain.MigrationRepo
import doobie.implicits._
import doobie.util.fragment.Fragment
import io.github.gaelrenoux.tranzactio.doobie._
import io.github.gaelrenoux.tranzactio.{DbException, doobie => _}
import zio.{Has, Task, ZIO, ZLayer}

object DbMigrationRepo {
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

  val live: ZLayer[Database, DbException, Has[MigrationRepo.Service]] = {
    def migrationsFromInclusiveTx(migrations: List[Fragment], version: Int): ZIO[Connection, DbException, Unit] =
      ZIO
        .foreach(migrations.drop(version)) { s =>
          tzio {
            s.update.run.map(_ => ())
          }
        }
        .unit

    def allMigrationsTx(migrations: List[Fragment]): ZIO[Connection, DbException, Unit] = migrationsFromInclusiveTx(migrations, 0)

    val r: ZIO[Database, DbException, MigrationRepo.Service] = ZIO.accessM[Has[Database.Service]] { db =>
      val database = db.get[Database.Service]

      def internalMigrate(module: String, allMigrations: List[Fragment]): ZIO[Has[Database.Service], DbException, Unit] =
        for {
          lastVersion <- database.transactionOrDie {
            tzio[Int] {
              lastVersionQuery(module).unique
            }.catchAll { _ =>
              allMigrationsTx(allMigrations) *> ZIO.succeed(allMigrations.size)
            }
          }
          _ <- database.transactionOrDie {
            migrationsFromInclusiveTx(allMigrations, lastVersion + 1)
          }
          _ <- database.transactionOrDie(tzio(updateVersionQuery(module, allMigrations.size).run.map(_ => ())))
        } yield ()

      internalMigrate("migrations", migrations).as(new MigrationRepo.Service {
        override def getLastVersion(module: String): Task[Int] =
          database.transactionOrDie(tzio(lastVersionQuery(module).unique))

        override def migrate(module: String, allMigrations: List[Fragment]): Task[Unit] =
          internalMigrate(module, allMigrations).provide(db)
      })
    }

    r.toLayer
  }
}
