package com.github.vsuharnikov.barbarissa.backend.employee

import doobie.util.{Get, Put}

case class CompanyId(asString: String)
object CompanyId {
  implicit val companyIdGet: Get[CompanyId] = Get[String].tmap(CompanyId(_))
  implicit val companyIdPut: Put[CompanyId] = Put[String].contramap(_.asString)
}
