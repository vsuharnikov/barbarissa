package com.github.vsuharnikov.barbarissa.backend.shared.domain

import zio.config.magnolia.DeriveConfigDescriptor.Descriptor

case class AbsenceId(asString: String)
object AbsenceId {
  implicit val absenceIdDescriptor: Descriptor[AbsenceId] = Descriptor[String].xmap(AbsenceId(_), _.asString)
}
