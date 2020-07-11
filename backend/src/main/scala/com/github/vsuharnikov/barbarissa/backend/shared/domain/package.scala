package com.github.vsuharnikov.barbarissa.backend.shared

import zio.Has

package object domain {
  type ReportService = Has[ReportService.Service]
}
