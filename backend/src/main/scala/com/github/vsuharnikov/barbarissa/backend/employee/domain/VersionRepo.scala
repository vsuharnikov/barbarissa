package com.github.vsuharnikov.barbarissa.backend.employee.domain

import zio.Task
import zio.macros.accessible

@accessible
object VersionRepo {
  trait Service extends Serializable {
    def getLastVersion(module: String): Task[Int]
    def updateLastVersion(module: String, newVersion: Int): Task[Unit]
  }
}
