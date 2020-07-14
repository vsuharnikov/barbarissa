package com.github.vsuharnikov.barbarissa.backend.shared.infra

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Inflection, Sex}
import padeg.lib.Padeg

object PadegInflection extends Inflection {
  private val dativeCase = 2

  def dativeAppointment(nominativeAppointment: String): String = Padeg.getAppointmentPadeg(nominativeAppointment, dativeCase)

  def dativeName(name: String, sex: Option[Sex] = None): String = sex match {
    case None => Padeg.getFIOPadegFSAS(name, dativeCase)
    case Some(sex) => Padeg.getFIOPadegFS(name, toPadeg(sex), dativeCase)
  }

  // http://www.delphikingdom.com/asp/viewitem.asp?catalogid=412#Header_16584387661296
  private def toPadeg(sex: Sex): Boolean = sex match {
    case Sex.Male => true
    case Sex.Female => false
  }
}
