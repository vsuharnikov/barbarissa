package com.github.vsuharnikov.barbarissa.backend.employee.domain

import com.github.vsuharnikov.barbarissa.backend.employee.EmployeeId

// id is Jira's username
case class Employee(id: EmployeeId, name: String, email: String, localizedName: Option[String], position: Option[String])