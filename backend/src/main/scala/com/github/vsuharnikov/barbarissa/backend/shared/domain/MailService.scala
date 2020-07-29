package com.github.vsuharnikov.barbarissa.backend.shared.domain

import com.github.vsuharnikov.barbarissa.backend.shared.infra.MsExchangeService
import microsoft.exchange.webservices.data.core.service.item.EmailMessage
import microsoft.exchange.webservices.data.property.complex.MessageBody
import zio.blocking.Blocking
import zio.macros.accessible
import zio.{Has, Task, ZLayer}

@accessible
object MailService {
  type FileName    = String
  type FileContent = Array[Byte]
  case class EmailAddress(asString: String)

  trait Service {
    def send(to: EmailAddress, subject: String, bodyText: String, attachments: Map[FileName, FileContent]): Task[Unit]
  }

  // TODO Retries
  // TODO Separate file
  val live = ZLayer.fromServicesM[Blocking.Service, MsExchangeService.Service, Any, Throwable, Service] { (blocking, msExchange) =>
    msExchange.get.map { service =>
      new Service {
        override def send(to: EmailAddress, subject: String, bodyText: String, attachments: Map[FileName, FileContent]): Task[Unit] =
          blocking
            .effectBlockingIO {
              val message = new EmailMessage(service)
              message.setSubject(subject)
              message.setBody(MessageBody.getMessageBodyFromText(bodyText))
              message.getToRecipients.add(to.asString)
              val messageAttachments = message.getAttachments
              attachments.foreach {
                case (name, content) => messageAttachments.addFileAttachment(name, content)
              }
              message.send()
            }
            .provide(Has(blocking))
      }
    }
  }
}
