package com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange

import java.net.URI

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl
import microsoft.exchange.webservices.data.core.ExchangeService
import microsoft.exchange.webservices.data.core.enumeration.misc.{ExchangeVersion, TraceFlags}
import microsoft.exchange.webservices.data.credential.WebCredentials
import zio.blocking.{Blocking, effectBlockingIO}
import zio.logging._
import zio.{Has, ZIO}

object MsExchangeService {
  case class Config(apiTarget: ApiTargetConfig, credentials: CredentialsConfig)
  case class CredentialsConfig(username: String, password: String)
  sealed trait ApiTargetConfig extends Product with Serializable
  object ApiTargetConfig {
    case object AutoDiscover      extends ApiTargetConfig
    case class Fixed(url: String) extends ApiTargetConfig
  }

  type Dependencies = Has[Config] with Blocking with Logging

  val live = ZIO
    .accessM[Dependencies] { env =>
      val config     = env.get[Config]
      val annotation = LogAnnotation.Name("MsExchangeService" :: Nil)

      log.locally(annotation) {
        log.info("Initializing Exchange API") *>
          effectBlockingIO {
            val service = new ExchangeService(ExchangeVersion.Exchange2010_SP2)

            service.setTraceEnabled(true)
            service.setTraceFlags(java.util.EnumSet.of(TraceFlags.EwsRequest, TraceFlags.EwsResponse))
            service.setTraceListener { (traceType: String, traceMessage: String) =>
              zio.Runtime.default.unsafeRun {
                log
                  .locally(annotation) {
                    log.info(s"[type=$traceType] ${traceMessage.replace("\n", "  ")}")
                  }
                  .provide(env)
              }
            }

            val credentials = new WebCredentials(config.credentials.username, config.credentials.password)
            service.setCredentials(credentials)
            config.apiTarget match {
              case ApiTargetConfig.Fixed(url)   => service.setUrl(URI.create(url))
              case ApiTargetConfig.AutoDiscover => service.autodiscoverUrl(config.credentials.username, new RedirectionUrlCallback)
            }

            service
          }.tap { service =>
            log.info(s"URL of API: ${service.getUrl}")
          }
      }
    }
    .toLayer

  class RedirectionUrlCallback extends IAutodiscoverRedirectionUrl {
    override def autodiscoverRedirectionUrlValidationCallback(redirectionUrl: String): Boolean =
      redirectionUrl.toLowerCase.startsWith("https://")
  }
}
