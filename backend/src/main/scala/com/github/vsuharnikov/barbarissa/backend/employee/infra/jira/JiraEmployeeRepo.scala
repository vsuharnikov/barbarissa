package com.github.vsuharnikov.barbarissa.backend.employee.infra.jira

import com.github.vsuharnikov.barbarissa.backend.employee.app.JsonEntitiesEncoding
import com.github.vsuharnikov.barbarissa.backend.employee.domain.{Employee, EmployeeRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.entities.{JiraBasicUserData, JiraExtendedUserData}
import com.github.vsuharnikov.barbarissa.backend.employee.{CompanyId, EmployeeId}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.Sex
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import zio.logging.{Logging, log}
import zio.{Has, Task, ZIO, ZLayer}

object JiraEmployeeRepo {
  type Dependencies = Logging with JiraApi

  val live: ZLayer[Dependencies, Nothing, Has[EmployeeRepo.Service]] =
    ZIO
      .access[Dependencies] { env =>
        new EmployeeRepo.Service with JsonEntitiesEncoding[Task] {
          override def update(draft: Employee): Task[Unit] = {
            log.info(s"Updating '${draft.employeeId.asString}'") *>
              JiraApi
                .setUserExtendedData(
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
          }.provide(env)

          override def get(by: EmployeeId): Task[Option[Employee]] = {
            log.info(s"Getting '${by.asString}'") *>
              JiraApi.getUserBasicData(by.asString) <&>
              JiraApi.getUserExtendedData(by.asString).map(_.getOrElse(JiraExtendedUserData.empty)) map
              Function.tupled(toDomain)
          }.provide(env)
        }
      }
      .toLayer

  def toDomain(basic: Option[JiraBasicUserData], extended: JiraExtendedUserData): Option[Employee] = basic.map { basic =>
    Employee(
      employeeId = EmployeeId(basic.name),
      name = basic.displayName,
      localizedName = extended.localizedName,
      email = basic.emailAddress,
      companyId = extended.companyId.map(CompanyId),
      position = extended.position,
      sex = extended.sex.map {
        case "male"   => Sex.Male
        case "female" => Sex.Female
      }
    )
  }
}
