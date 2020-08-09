package com.github.vsuharnikov.barbarissa.backend.shared.domain

sealed abstract class DomainError extends RuntimeException("", null, true, false) {
  def name: String = getClass.getSimpleName // WARN: JRE 9+ https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8057919
}

object DomainError {
  case class UnhandledError(details: String) extends DomainError {
    override def getMessage: String = s"An unexpected error: $details. Ask an administrator" // TODO Add time
  }

  case class NotEnoughData(details: String) extends DomainError {
    override def getMessage: String = s"Not enough data to continue: $details. Try to fill it"
  }

  case class ConfigurationError(override val getMessage: String) extends DomainError

  case class RemoteCallFailed(service: String) extends DomainError {
    override def getMessage: String = s"Call to '$service' failed. Try later"
  }

  case class NotFound(klass: String, id: String) extends DomainError {
    override def getMessage: String = s"Can't find '$klass' with id of '$id'"
  }
}
