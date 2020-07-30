package com.github.vsuharnikov.barbarissa.backend.employee.infra

import cats.syntax.applicative._
import cats.syntax.apply._
import com.github.vsuharnikov.barbarissa.backend.employee.domain.MigrationRepo
import com.github.vsuharnikov.barbarissa.backend.employee.domain.MigrationRepo.Migrations
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.DbTransactor
import doobie.ConnectionIO
import doobie.implicits._
import zio.interop.catz._
import zio.{Has, Task, ZIO, ZLayer}

object DbMigrationRepo {
  private def updateVersion(module: String, draftVersion: Int) =
    sql"""INSERT OR REPLACE INTO LastMigration(module, version) VALUES ($module, $draftVersion)""".update.run.map(_ => ())

  private val migrations: Migrations = List(
    sql"""CREATE TABLE LastMigration(
module VARCHAR(100) PRIMARY KEY,
version INTEGER NOT NULL
)""".update.run.map(_ => ()),
    updateVersion("migrations", 1)
  )

  private def lastVersion(module: String) = sql"SELECT version FROM LastMigration WHERE module = $module".query[Int].unique

  def migrationsFromInclusiveTx(migrations: Migrations, version: Int) =
    migrations.drop(version).foldLeft(().pure[ConnectionIO])(_ *> _)

  val live: ZLayer[DbTransactor, Throwable, Has[MigrationRepo.Service]] = ZIO
    .accessM[DbTransactor] { env =>
      val tr = env.get[DbTransactor.Service].transactor

      def internalMigrate(module: String, allMigrations: Migrations) =
        for {
          lastVersion <- lastVersion(module).transact(tr).catchAll(_ => ZIO.succeed(-1))
          _ <- {
            migrationsFromInclusiveTx(allMigrations, lastVersion + 1) *>
              updateVersion(module, allMigrations.size)
          }.transact(tr)
        } yield ()

      internalMigrate("migrations", migrations).as(new MigrationRepo.Service {
        override def getLastVersion(module: String): Task[Int] = lastVersion(module).transact(tr)

        override def migrate(module: String, allMigrations: Migrations): Task[Unit] = internalMigrate(module, allMigrations)
      })
    }
    .toLayer
}
