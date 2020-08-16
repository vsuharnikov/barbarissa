package com.github.vsuharnikov.barbarissa.backend.shared.app

import org.http4s.rho.RhoRoutes
import zio.{RIO, URIO}

trait ApiRoutes[R] extends Serializable {
  type HttpIO[A]   = RIO[R, A]
  type HttpURIO[A] = URIO[R, A]

  def rhoRoutes: RhoRoutes[HttpIO]
}
