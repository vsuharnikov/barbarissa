package com.github.vsuharnikov.barbarissa.backend.employee.domain

import doobie.ConnectionIO
import doobie.util.fragment.Fragment
import zio.Task
import zio.macros.accessible

@accessible
object MigrationRepo extends Serializable {
  type Migrations = List[ConnectionIO[Unit]] // TODO abstract

  trait Service extends Serializable {
    def getLastVersion(module: String): Task[Int]
    def migrate(module: String, allMigrations: Migrations): Task[Unit]
  }
}
