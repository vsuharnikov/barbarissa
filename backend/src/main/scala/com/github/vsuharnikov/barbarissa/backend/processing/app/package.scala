package com.github.vsuharnikov.barbarissa.backend.processing

import zio.Has

package object app {
  type ProcessingHttpApiRoutes = Has[ProcessingHttpApiRoutes.Service]
}
