package com.github.vsuharnikov.barbarissa.backend.employee.infra.db

import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.EmployeeId
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.H2DbTransactor.TransactorIO
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.MigrationRepo
import doobie.implicits._
import doobie.util.update.Update
import zio.interop.catz._
import zio.logging.{Logger, Logging}
import zio.{Has, Task, ZIO, ZLayer}

object DbCachedEmployeeRepo {

  type Dependencies = Logging with Has[TransactorIO] with MigrationRepo with EmployeeRepo

  val live: ZLayer[Dependencies, Throwable, Has[EmployeeRepo.Service]] = ZIO
    .accessM[Dependencies] { env =>
      val tr            = env.get[TransactorIO]
      val migrationRepo = env.get[MigrationRepo.Service]
      val underlying    = env.get[EmployeeRepo.Service]
      val log           = env.get[Logger[String]]

      migrationRepo
        .migrate("Employee", Sql.migrations)
        .as(new EmployeeRepo.Service {
          override def update(draft: Employee): Task[Unit] = {
            for {
              orig <- get(draft.employeeId)
              _ <- {
                if (orig.contains(draft)) log.debug(s"Nothing to update in ${draft.employeeId.asString}")
                else underlying.update(draft) *> Sql.update.run(draft).transact(tr).unit
              }
            } yield ()
          }

          override def get(by: EmployeeId): Task[Option[Employee]] =
            Sql.get(by.asString).transact(tr).flatMap {
              case None =>
                for {
                  r <- underlying.get(by)
                  _ <- ZIO.foreach(r) { r =>
                    Sql.update.run(r).transact(tr) *> log.info(s"Caching ${by.asString}")
                  }
                } yield r
              case r => ZIO.succeed(r)
            }

          override def search(byEmail: String): Task[Option[Employee]] =
            Sql.getByEmail(byEmail).transact(tr).flatMap {
              case None =>
                for {
                  r <- underlying.search(byEmail)
                  _ <- ZIO.foreach(r) { r =>
                    Sql.update.run(r).transact(tr) *> log.info(s"Caching ${r.employeeId.asString}")
                  }
                } yield r
              case r => ZIO.succeed(r)
            }
        })
    }
    .toLayer

  object Sql extends DbEntitiesEncoding {
    val migrations = List(sql"""CREATE TABLE Employee(
employeeId VARCHAR(100) PRIMARY KEY NOT NULL,
name VARCHAR(100) NOT NULL,
email VARCHAR(100) NOT NULL,
localizedName VARCHAR(255) NULL,
companyId VARCHAR(100) NULL,
position VARCHAR(100) NULL,
sex INTEGER NULL
)""").map(_.update.run.map(_ => ()))

    val update = Update[Employee]("""MERGE INTO
Employee(employeeId, name, email, localizedName, companyId, position, sex)
VALUES(?, ?, ?, ?, ?, ?, ?)""")

    def get(employeeId: String) = sql"""SELECT
employeeId, name, email, localizedName, companyId, position, sex
FROM Employee WHERE employeeId = $employeeId
""".query[Employee].option

    def getByEmail(email: String) = sql"""SELECT
employeeId, name, email, localizedName, companyId, position, sex
FROM Employee WHERE email = $email
""".query[Employee].option
  }
}
