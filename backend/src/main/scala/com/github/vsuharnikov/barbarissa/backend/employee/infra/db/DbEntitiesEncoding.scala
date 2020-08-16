package com.github.vsuharnikov.barbarissa.backend.employee.infra.db

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, CompanyId, EmployeeId}
import doobie.util.{Get, Put}

// TODO Required?
trait DbEntitiesEncoding {
  implicit val absenceIdGet: Get[AbsenceId] = Get[String].tmap(AbsenceId)
  implicit val absenceIdPut: Put[AbsenceId] = Put[String].contramap(_.asString)

  implicit val companyIdGet: Get[CompanyId] = Get[String].tmap(CompanyId)
  implicit val companyIdPut: Put[CompanyId] = Put[String].contramap(_.asString)

  implicit val employeeIdGet: Get[EmployeeId] = Get[String].tmap(EmployeeId)
  implicit val employeeIdPut: Put[EmployeeId] = Put[String].contramap(_.asString)
}
