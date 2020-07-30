package com.github.vsuharnikov.barbarissa.backend.shared.domain

import doobie.util.{Get, Put}

trait Inflection {
  def dativeAppointment(nominativeAppointment: String): String
  def dativeName(name: String, sex: Option[Sex] = None): String
}

sealed trait Sex extends Product with Serializable
object Sex {
  implicit val sexGet: Get[Sex] = Get[Int].tmap {
    case 1 => Male
    case 2 => Female
  }
  implicit val sexPut: Put[Sex] = Put[Int].contramap {
    case Male   => 1
    case Female => 2
  }

  case object Male   extends Sex
  case object Female extends Sex
}
