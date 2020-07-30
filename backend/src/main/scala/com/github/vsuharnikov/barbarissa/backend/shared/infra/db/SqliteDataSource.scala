package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.util.Properties

import javax.sql.DataSource
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import zio._
import zio.blocking.Blocking

object SqliteDataSource {
  case class Config(url: String, pragmas: Properties)

  val live: ZLayer[Has[Config] with Blocking, Throwable, Has[DataSource]] = {
    ZIO
      .accessM[Has[Config] with Blocking] { env =>
        val conf = env.get[Config]
        blocking.effectBlocking {
          val ds = new SQLiteDataSource(new SQLiteConfig(conf.pragmas))
          ds.setUrl(conf.url)
          ds
        }
      }
      .toLayer
  }

}
