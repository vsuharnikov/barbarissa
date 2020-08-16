package com.github.vsuharnikov.barbarissa.backend.shared.app

import org.http4s.rho.RhoRoutes
import zio.Task

trait HasRoutes extends Serializable {
  def rhoRoutes: RhoRoutes[Task]
}
