package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.util.concurrent.Executors

import cats.effect.Blocker
import doobie.Transactor
import javax.sql.DataSource
import zio.blocking.{Blocking, effectBlockingIO}
import zio.interop.catz._
import zio.logging.{LogAnnotation, Logging, log}
import zio.{Has, Task, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

object DbTransactor extends Serializable {
  type TransactorIO = Transactor[Task]

  type Dependencies = Blocking with Logging with Has[DataSource]

  val live: ZLayer[Dependencies, Throwable, Has[TransactorIO]] = ZIO
    .accessM[Dependencies] { env =>
      log
        .locally(LogAnnotation.Name("SqliteDataSource" :: Nil)) {
          log.info("Initializing") *>
            effectBlockingIO {
              val executor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
              Transactor.fromConnection[Task](
                // Only one connection for sqlite, https://stackoverflow.com/a/48480012
                connection = env.get[DataSource].getConnection,
                blocker = Blocker.liftExecutionContext(executor)
              )
            } <* log.info("Initialized")
        }
    }
    .toLayer
}
