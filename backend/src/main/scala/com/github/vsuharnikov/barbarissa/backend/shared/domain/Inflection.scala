package com.github.vsuharnikov.barbarissa.backend.shared.domain

trait Inflection {
  def dativeAppointment(nominativeAppointment: String): String
  def dativeName(name: String, sex: Option[Sex] = None): String
}

sealed trait Sex extends Product with Serializable
object Sex {
  case object Male   extends Sex
  case object Female extends Sex
}
