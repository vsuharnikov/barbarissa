package com.github.vsuharnikov.barbarissa.backend.queue

import zio.Has

package object domain {
  type AbsenceQueue = Has[AbsenceQueue.Service]
}
