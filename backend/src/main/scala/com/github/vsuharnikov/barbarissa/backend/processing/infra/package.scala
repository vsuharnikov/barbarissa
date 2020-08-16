package com.github.vsuharnikov.barbarissa.backend.processing

import zio.Has

package object infra {
  type ProcessingService = Has[ProcessingService.Service]
}
