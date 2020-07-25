package com.github.vsuharnikov.barbarissa.backend.employee

import zio.config.magnolia.DeriveConfigDescriptor.Descriptor

case class AbsenceReasonId(asString: String)
object AbsenceReasonId {
  implicit val absenceReasonIdDescriptor: Descriptor[AbsenceReasonId] = Descriptor[String].xmap(AbsenceReasonId(_), _.asString)
}
