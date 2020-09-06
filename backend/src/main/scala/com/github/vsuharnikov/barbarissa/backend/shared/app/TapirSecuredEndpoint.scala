package com.github.vsuharnikov.barbarissa.backend.shared.app

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util

import com.github.vsuharnikov.barbarissa.backend.shared.domain.DomainError
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.{auth, endpoint, header}
import zio.Task

object TapirSecuredEndpoint extends TapirCommonEntities {
  def apply(apiKeyHash: Array[Byte]): PartialServerEndpoint[Unit, Unit, Throwable, Unit, Nothing, Task] =
    endpoint
      .in(auth.apiKey[String](header[String]("X-Auth-Token")))
      .errorOut(errorOut)
      .serverLogicForCurrentRecoverErrors { token =>
        if (util.Arrays.equals(apiKeyHash, sha256Of(token))) Task.unit
        else Task.fail(DomainError.NotEnoughData("The X-Auth-Token header is required"))
      }

  private def sha256Of(message: String): Array[Byte] = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(message.getBytes(StandardCharsets.UTF_8))
  }
}
