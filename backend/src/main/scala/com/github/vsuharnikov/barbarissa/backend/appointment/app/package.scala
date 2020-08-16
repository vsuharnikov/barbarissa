package com.github.vsuharnikov.barbarissa.backend.appointment

import zio.Has

package object app {
  type AppointmentHttpApiRoutes = Has[AppointmentHttpApiRoutes.Service]
}
