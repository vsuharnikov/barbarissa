package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.shared.domain.{CompanyId, EmployeeId, Sex}

case class Employee(
    employeeId: EmployeeId,
    name: String,
    email: String,
    localizedName: Option[String],
    companyId: Option[CompanyId],
    position: Option[String],
    sex: Option[Sex]
)
