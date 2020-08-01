package com.github.vsuharnikov.barbarissa.backend.employee.infra.db

import com.github.vsuharnikov.barbarissa.backend.employee.EmployeeId
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo, MigrationRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.DbTransactor.TransactorIO
import doobie.implicits._
import doobie.util.update.Update
import zio.interop.catz._
import zio.{Has, Task, ZIO, ZLayer}

object DbCachedEmployeeRepo {

  type Dependencies = Has[TransactorIO] with MigrationRepo with EmployeeRepo

  val live: ZLayer[Dependencies, Throwable, Has[EmployeeRepo.Service]] = ZIO
    .accessM[Dependencies] { env =>
      val tr            = env.get[TransactorIO]
      val migrationRepo = env.get[MigrationRepo.Service]
      val underlying    = env.get[EmployeeRepo.Service]

      migrationRepo
        .migrate("Employee", Sql.migrations)
        .as(new EmployeeRepo.Service {
          override def update(draft: Employee): Task[Unit] =
            underlying.update(draft) *> Sql.update.run(draft).transact(tr).unit

          override def get(by: EmployeeId): Task[Option[Employee]] =
            Sql.get(by.asString).transact(tr).flatMap {
              case None =>
                for {
                  r <- underlying.get(by)
                  _ <- ZIO.foreach(r) { r =>
                    Sql.update.run(r).transact(tr)
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

    val update = Update[Employee]("""INSERT OR REPLACE INTO
Employee(employeeId, name, email, localizedName, companyId, position, sex)
VALUES(?, ?, ?, ?, ?, ?, ?)""")

    def get(employeeId: String) = sql"""SELECT
employeeId, name, email, localizedName, companyId, position, sex
FROM Employee WHERE employeeId = $employeeId
""".query[Employee].option
  }
}
