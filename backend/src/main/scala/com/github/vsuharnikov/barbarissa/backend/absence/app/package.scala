package com.github.vsuharnikov.barbarissa.backend.absence

import zio.Has

package object app {
  type AbsenceHttpApiRoutes = Has[AbsenceHttpApiRoutes.Service]
}
