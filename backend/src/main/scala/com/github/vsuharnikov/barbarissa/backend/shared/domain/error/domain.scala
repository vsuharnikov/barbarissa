package com.github.vsuharnikov.barbarissa.backend.shared.domain

import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs

package object error {

  sealed trait DomainError extends Product with Serializable

  object DomainError {
    implicit val domainErrorToArgs: ToArgs[DomainError] = ToArgs.gen[DomainError]

    implicit final class Ops(private val self: DomainError) extends AnyVal {
      def toArgs: List[(String, String)] = domainErrorToArgs.toArgs(self)
    }
  }

  case class ValidationError(field: String, actual: String, expected: String) extends DomainError

  case object ClaimNotRequired extends DomainError

  case object TemplateNotFound extends DomainError // ???

  sealed trait RepoError extends DomainError

  case object RepoRecordNotFound extends RepoError
  case object RepoRecordBroken   extends RepoError
  case object RepoNotAvailable   extends RepoError
  case object RepoUnknown        extends RepoError
}
