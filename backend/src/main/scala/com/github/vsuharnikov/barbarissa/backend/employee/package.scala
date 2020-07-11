package com.github.vsuharnikov.barbarissa.backend

import io.estatico.newtype.macros.newtype

package object employee {
  @newtype case class EmployeeId(asString: String)
  @newtype case class AbsenceId(asString: String)
}
