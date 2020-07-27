package com.github.vsuharnikov.barbarissa.backend.employee.domain

import doobie.util.fragment.Fragment
import zio.Task
import zio.macros.accessible

@accessible
object MigrationRepo {
  trait Service extends Serializable {
    def getLastVersion(module: String): Task[Int]
    def migrate(module: String, allMigrations: List[Fragment]): Task[Unit]
  }
}
