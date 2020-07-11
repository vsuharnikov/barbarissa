package com.github.vsuharnikov.barbarissa.backend.shared.infra

import java.io.{ByteArrayOutputStream, File}

import com.deepoove.poi.XWPFTemplate
import com.github.vsuharnikov.barbarissa.backend.shared.domain.ReportService
import zio.{Task, UIO, ZLayer}

import scala.jdk.CollectionConverters.MapHasAsJava

object DocxReportService {
  val live = ZLayer.succeed[ReportService.Service] {
    new ReportService.Service {
      override def generate(templateFile: File, args: Map[String, String]): UIO[Array[Byte]] =
        UIO.succeed(XWPFTemplate.compile(templateFile)).map { template => // TODO
          val output = new ByteArrayOutputStream(1024)
          template.render(args.asJava).write(output)
          output.toByteArray
        }
    }
  }
}
