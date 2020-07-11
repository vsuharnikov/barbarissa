package com.github.vsuharnikov.barbarissa.backend.shared.domain

import java.io.File

import zio.UIO
import zio.macros.accessible

@accessible
object ReportService {
  trait Service {
    def generate(templateFile: File, args: Map[String, String]): UIO[Array[Byte]] // TODO
  }
}
