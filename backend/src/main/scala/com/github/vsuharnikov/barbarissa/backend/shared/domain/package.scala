package com.github.vsuharnikov.barbarissa.backend.shared

import zio.Has

package object domain {
  type ReportService = Has[ReportService.Service]
  type MailService   = Has[MailService.Service]

  // https://github.com/zio/zio-intellij/issues/129
  //  @newtype case class EmployeeId(asString: String)
  //  @newtype case class AbsenceId(asString: String)
}
