package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraBasicUserData, JiraExtendedUserData}
import com.github.vsuharnikov.barbarissa.backend.employee.{CompanyId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.app.JsonSupport
import com.github.vsuharnikov.barbarissa.backend.shared.domain.Sex
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import zio.{Task, ZLayer}

object JiraEmployeeRepo {
  val live = ZLayer.fromService[JiraApi.Service, EmployeeRepo.Service] { api =>
    new EmployeeRepo.Service with JsonSupport[Task] {
      override def update(draft: Employee): Task[Unit] = api.setUserExtendedData(
        username = draft.employeeId.asString,
        JiraExtendedUserData(
          localizedName = draft.localizedName,
          position = draft.position,
          companyId = draft.companyId.map(_.asString),
          sex = draft.sex.map {
            case Sex.Male   => "male"
            case Sex.Female => "female"
          }
        )
      )

      override def get(by: EmployeeId): Task[Option[Employee]] =
        api.getUserBasicData(by.asString) <&>
          api.getUserExtendedData(by.asString).map(_.getOrElse(JiraExtendedUserData.empty)) map
          Function.tupled(toDomain)
    }
  }

  def toDomain(basic: Option[JiraBasicUserData], extended: JiraExtendedUserData): Option[Employee] = basic.map { basic =>
    Employee(
      employeeId = EmployeeId(basic.name),
      name = basic.displayName,
      localizedName = extended.localizedName,
      email = basic.emailAddress,
      companyId = extended.companyId.map(CompanyId(_)),
      position = extended.position,
      sex = extended.sex.map {
        case "male"   => Sex.Male
        case "female" => Sex.Female
      }
    )
  }
}
