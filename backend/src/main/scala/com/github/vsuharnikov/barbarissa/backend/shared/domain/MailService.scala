package com.github.vsuharnikov.barbarissa.backend.shared.domain

import zio.Task
import zio.macros.accessible

@accessible
object MailService {
  type FileName    = String
  type FileContent = Array[Byte]
  case class EmailAddress(asString: String)

  trait Service {
    def send(to: EmailAddress, subject: String, bodyText: String, attachments: Map[FileName, FileContent]): Task[Unit]
  }
}
