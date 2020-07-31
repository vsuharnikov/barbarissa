package com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange

import java.net.URI

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl
import microsoft.exchange.webservices.data.core.ExchangeService
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion
import microsoft.exchange.webservices.data.credential.WebCredentials
import zio.blocking.Blocking
import zio.{ZIO, ZLayer}

object MsExchangeService {
  case class Config(apiTarget: ApiTargetConfig, credentials: CredentialsConfig)
  case class CredentialsConfig(username: String, password: String)
  sealed trait ApiTargetConfig extends Product with Serializable
  object ApiTargetConfig {
    case object AutoDiscover      extends ApiTargetConfig
    case class Fixed(url: String) extends ApiTargetConfig
  }

  val live = ZLayer.fromServicesM[Config, Blocking.Service, Any, Throwable, ExchangeService] { (config, blocking) =>
    blocking
      .blocking {
        ZIO.effect {
          val service = new ExchangeService(ExchangeVersion.Exchange2010_SP2)

//          service.setTraceEnabled(true)
//          service.setTraceFlags(java.util.EnumSet.allOf(classOf[TraceFlags]))
//          service.setTraceListener((traceType: String, traceMessage: String) => println(s"==> type: $traceType, traceMessage: $traceMessage"))

          val credentials = new WebCredentials(config.credentials.username, config.credentials.password)
          service.setCredentials(credentials)
          config.apiTarget match {
            case ApiTargetConfig.Fixed(url)   => service.setUrl(URI.create(url))
            case ApiTargetConfig.AutoDiscover => service.autodiscoverUrl(config.credentials.username, new RedirectionUrlCallback)
          }

          // TODO log url
          service
        }
      }
  }

  class RedirectionUrlCallback extends IAutodiscoverRedirectionUrl {
    override def autodiscoverRedirectionUrlValidationCallback(redirectionUrl: String): Boolean =
      redirectionUrl.toLowerCase.startsWith("https://")
  }
}
