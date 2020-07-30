package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.util.concurrent.Executors

import cats.effect.Blocker
import doobie.Transactor
import javax.sql.DataSource
import zio.interop.catz._
import zio.{Task, ZLayer}

import scala.concurrent.ExecutionContext

object DbTransactor extends Serializable {
  type TransactorIO = Transactor[Task]

  val live = ZLayer.fromService[DataSource, TransactorIO] { ds =>
    val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    Transactor.fromConnection[Task](
      connection = ds.getConnection, // Only one connection for sqlite, https://stackoverflow.com/a/48480012
      blocker = Blocker.liftExecutionContext(executor)
    )
  }
}
