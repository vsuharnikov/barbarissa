package com.github.vsuharnikov.barbarissa.backend.shared.infra.db

import java.io.{File, IOException}
import java.nio.file.Files
import java.util.Properties

import javax.sql.DataSource
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import zio._
import zio.blocking.{Blocking, effectBlockingIO}
import zio.logging._

object SqliteDataSource {
  case class Config(dir: File, url: String, pragmas: Properties)

  type Dependencies = Has[Config] with Blocking with Logging

  val live: ZLayer[Dependencies, IOException, Has[DataSource]] = ZIO
    .accessM[Dependencies] { env =>
      val conf = env.get[Config]
      log
        .locally(LogAnnotation.Name("SqliteDataSource" :: Nil)) {
          log.info("Initializing") *>
            effectBlockingIO {
              Files.createDirectories(conf.dir.toPath)
              val ds = new SQLiteDataSource(new SQLiteConfig(conf.pragmas))
              ds.setUrl(conf.url)
              ds
            } <* log.info("Initialized")
        }
    }
    .toLayer

}
