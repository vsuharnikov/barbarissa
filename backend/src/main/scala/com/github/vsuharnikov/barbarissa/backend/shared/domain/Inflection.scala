package com.github.vsuharnikov.barbarissa.backend.shared.domain

import doobie.util.{Get, Put}

trait Inflection {
  def dativeAppointment(nominativeAppointment: String): String
  def dativeName(name: String, sex: Option[Sex] = None): String
  def pluralize(x: Int, textForms: (String, String, String)): String = {
    val n100 = math.abs(x) % 100
    val n1   = n100        % 10
    if (n100 > 10 && n100 < 20) textForms._3
    else if (n1 > 1 && n1 < 5) textForms._2
    else if (n1 == 1) textForms._1
    else textForms._3
  }
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
