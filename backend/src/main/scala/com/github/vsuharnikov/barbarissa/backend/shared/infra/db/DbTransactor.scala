package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.util.concurrent.Executors

import cats.effect.Blocker
import doobie.Transactor
import javax.sql.DataSource
import zio.interop.catz._
import zio.{Task, ZLayer}

import scala.concurrent.ExecutionContext

object DbTransactor extends Serializable {
  trait Service extends Serializable {
    val transactor: Transactor[Task]
  }

  val live = ZLayer.fromService[DataSource, Service] { ds =>
    new Service {
      private val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

      override val transactor: Transactor[Task] = Transactor
        .fromDataSource[Task]
        .apply(
          dataSource = ds,
          connectEC = executor,
          blocker = Blocker.liftExecutionContext(executor)
        )
    }
  }
}
