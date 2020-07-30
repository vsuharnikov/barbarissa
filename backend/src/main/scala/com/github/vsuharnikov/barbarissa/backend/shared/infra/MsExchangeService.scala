package com.github.vsuharnikov.barbarissa.backend.shared.infra

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl
import microsoft.exchange.webservices.data.core.ExchangeService
import microsoft.exchange.webservices.data.core.enumeration.misc.{ExchangeVersion, TraceFlags}
import microsoft.exchange.webservices.data.credential.WebCredentials
import zio.blocking.Blocking
import zio.{ZIO, ZLayer}

object MsExchangeService {
  case class CredentialsConfig(username: String, password: String)
  case class Config(credentials: CredentialsConfig)

  // TODO laziness
  val live = ZLayer.fromServicesM[Config, Blocking.Service, Any, Throwable, ExchangeService] { (config, blocking) =>
    blocking
      .blocking {
        ZIO.effect {
          val service = new ExchangeService(ExchangeVersion.Exchange2010_SP2)
          service.setTraceEnabled(true)
          service.setTraceFlags(java.util.EnumSet.allOf(classOf[TraceFlags]))
          service.setTraceListener((traceType: String, traceMessage: String) => println(s"==> type: $traceType, traceMessage: $traceMessage"))

          val credentials = new WebCredentials(config.credentials.username, config.credentials.password)
          service.setCredentials(credentials)
          service.autodiscoverUrl(config.credentials.username, new RedirectionUrlCallback)
          service
        }
      }
  }

  class RedirectionUrlCallback extends IAutodiscoverRedirectionUrl {
    override def autodiscoverRedirectionUrlValidationCallback(redirectionUrl: String): Boolean =
      redirectionUrl.toLowerCase.startsWith("https://")
  }
}
