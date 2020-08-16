package com.github.vsuharnikov.barbarissa.backend.queue

import zio.Has

package object app {
  type QueueHttpApiRoutes = Has[QueueHttpApiRoutes.Service]
}
