package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.{CompanyId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.Sex

case class Employee(
    id: EmployeeId,
    name: String,
    email: String,
    localizedName: Option[String],
    companyId: Option[CompanyId],
    position: Option[String],
    sex: Option[Sex]
)
