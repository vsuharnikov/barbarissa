package com.github.vsuharnikov.barbarissa.backend.shared.app

case class ErrorTemplate(name: String, template: String) {
  def toMessage(args: List[(String, String)]): String = args.foldLeft(template) { case (r, (label, value)) =>
    r.replace(s"$${$label}", value)
  }
}
