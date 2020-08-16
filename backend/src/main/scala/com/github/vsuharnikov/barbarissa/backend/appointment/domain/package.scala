package com.github.vsuharnikov.barbarissa.backend.appointment

import zio.Has

package object domain {
  type AppointmentService = Has[AppointmentService.Service]
}
