package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.io.File
import java.util.concurrent.Executors

import cats.effect.Blocker
import doobie.Transactor
import doobie.h2._
import zio.interop.catz._
import zio.{Has, Task, ZLayer, ZManaged}

import scala.concurrent.ExecutionContext

object H2DbTransactor extends Serializable {
  case class Config(dir: File, url: String)

  type TransactorIO = Transactor[Task]

  def mkTransactor(
      conf: Config,
      connectEC: ExecutionContext,
      transactEC: ExecutionContext
  ): ZManaged[Any, Throwable, H2Transactor[Task]] =
    H2Transactor
      .newH2Transactor[Task](
        conf.url,
        "sa",
        "",
        connectEC,
        Blocker.liftExecutionContext(transactEC)
      )
      .toManagedZIO

  val live: ZLayer[Has[Config], Throwable, Has[TransactorIO]] = ZLayer.fromFunctionManaged[Has[Config], Throwable, TransactorIO] { env =>
    mkTransactor(
      env.get[Config],
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4)),
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
    )
  }
}
