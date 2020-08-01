package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.io.IOException
import java.util.Properties

import javax.sql.DataSource
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import zio._
import zio.blocking.{Blocking, effectBlockingIO}
import zio.logging._

object SqliteDataSource {
  case class Config(url: String, pragmas: Properties)

  type Dependencies = Has[Config] with Blocking with Logging

  val live: ZLayer[Dependencies, IOException, Has[DataSource]] = ZIO
    .accessM[Dependencies] { env =>
      val conf = env.get[Config]
      log
        .locally(LogAnnotation.Name("SqliteDataSource" :: Nil)) {
          log.info("Initializing") *>
            effectBlockingIO {
              val ds = new SQLiteDataSource(new SQLiteConfig(conf.pragmas))
              ds.setUrl(conf.url)
              ds
            } <* log.info("Initialized")
        }
    }
    .toLayer

}
