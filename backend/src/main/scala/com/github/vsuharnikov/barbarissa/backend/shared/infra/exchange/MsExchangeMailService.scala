package com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange

import java.util.UUID

import com.github.vsuharnikov.barbarissa.backend.shared.domain.MailService.{EmailAddress, FileContent, FileName, Service}
import microsoft.exchange.webservices.data.core.ExchangeService
import microsoft.exchange.webservices.data.core.service.item.EmailMessage
import microsoft.exchange.webservices.data.property.complex.MessageBody
import zio.blocking.{Blocking, effectBlockingIO}
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio.{Has, Task, ZIO, ZLayer}

object MsExchangeMailService {
  case class Config(retryPolicy: MsExchangeService.RetryPolicyConfig)

  type Dependencies = Has[Config] with Clock with Blocking with Logging with Has[ExchangeService]

  val live: ZLayer[Dependencies, Nothing, Has[Service]] = ZIO
    .access[Dependencies] { env =>
      val config      = env.get[Config]
      val service     = env.get[ExchangeService]
      val retryPolicy = MsExchangeService.retryPolicy(config.retryPolicy).provide(env)
      new Service {
        override def send(to: EmailAddress, subject: String, bodyText: String, attachments: Map[FileName, FileContent]): Task[Unit] = {
          val id = UUID.randomUUID().toString // TODO
          log.info(s"Sending '$id' with subject '$subject' to '${to.asString}'") *>
            effectBlockingIO {
              val message = new EmailMessage(service)
              message.setSubject(subject)
              message.setBody(MessageBody.getMessageBodyFromText(bodyText))
              message.getToRecipients.add(to.asString)
              val messageAttachments = message.getAttachments
              attachments.foreach {
                case (name, content) => messageAttachments.addFileAttachment(name, content)
              }
              message.send()
            } <* log.info(s"Sent '$id'")
        }.retry(retryPolicy).provide(env)
      }
    }
    .toLayer
}
