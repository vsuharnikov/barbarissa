package com.github.vsuharnikov.barbarissa.backend.shared

import zio.Has

package object infra {
  type MsExchangeService = Has[MsExchangeService.Service]
}
