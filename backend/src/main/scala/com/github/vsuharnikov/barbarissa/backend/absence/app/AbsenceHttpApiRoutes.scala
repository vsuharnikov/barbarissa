package com.github.vsuharnikov.barbarissa.backend.absence.app

import cats.implicits._
import com.github.vsuharnikov.barbarissa.backend.absence.app.entities.HttpV0Absence
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.macros.accessible
import zio.{Task, ZIO, ZLayer}
import sttp.tapir._
import sttp.tapir.docs.openapi._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir._

@accessible
object AbsenceHttpApiRoutes extends Serializable {
  trait Service extends HasHttp4sRoutes

  val live = ZLayer.fromServices[AbsenceRepo.Service, AbsenceReasonRepo.Service, Service] { (absenceRepo, absenceReasonRepo) =>
    new Service with TapirCommonEntities with JsonEntitiesEncoding[Task] {
      val getAbsence = endpoint.get
        .in("api" / "v0" / "employee" / employeeIdPath / "absence" / searchCursorQuery)
        .out(jsonBody[ListResponse[HttpV0Absence]])
        .errorOut(errorOut)

      val getAbsenceRoute: HttpRoutes[Task] = getAbsence.toRoutes {
        case (employeeId, cursor) =>
          for {
            absences       <- absenceRepo.getByCursor(AbsenceRepo.GetCursor(employeeId, cursor.fold(0)(_.startAt), cursor.fold(10)(_.maxResults)))
            absenceReasons <- absenceReasonRepo.all
            body <- {
              val (as, nextCursor) = absences
              val r = as.foldLeft(List.empty[HttpV0Absence].asRight[List[String]]) {
                case (r, a) =>
                  absenceReasons.get(a.reasonId) match {
                    case Some(ar) => r.map(httpAbsenceFrom(a, ar) :: _)
                    case None     => r.leftMap(a.reasonId.asString :: _)
                  }
              }
              r match {
                case Left(e) => ZIO.fail(ApiError.from(DomainError.ConfigurationError(s"Unknown absence reasons: ${e.mkString(", ")}")))
                case Right(r) =>
                  ZIO.succeed(
                    ListResponse(
                      r,
                      nextCursor.map(c => HttpSearchCursor(c.startAt, c.maxResults))
                    ))
              }
            }
          } yield body
      }

      override val http4sRoutes = getAbsenceRoute
      override val openApiDocs  = List(getAbsence).toOpenAPI("", "")

//      override val rhoRoutes: RhoRoutes[Task] = new RhoRoutes[Task] {
//        val parsers = new RoutesParsers[Task]()
//        import parsers._
//
//        "Gets an employee's absences" **
//          "absence" @@
//            GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") / "absence" +? param[Option[HttpSearchCursor]]("cursor") |>> {
//          (id: EmployeeId, cursor: Option[HttpSearchCursor]) =>
//            for {
//              absences       <- absenceRepo.getByCursor(AbsenceRepo.GetCursor(id, cursor.fold(0)(_.startAt), cursor.fold(10)(_.maxResults)))
//              absenceReasons <- absenceReasonRepo.all
//              body <- {
//                val (as, nextCursor) = absences
//                val r = as.foldLeft(List.empty[HttpV0Absence].asRight[List[String]]) {
//                  case (r, a) =>
//                    absenceReasons.get(a.reasonId) match {
//                      case Some(ar) => r.map(httpAbsenceFrom(a, ar) :: _)
//                      case None     => r.leftMap(a.reasonId.asString :: _)
//                    }
//                }
//                r match {
//                  case Left(e) => ZIO.fail(ApiError.from(DomainError.ConfigurationError(s"Unknown absence reasons: ${e.mkString(", ")}")))
//                  case Right(r) =>
//                    ZIO.succeed(
//                      ListResponse(
//                        r,
//                        nextCursor.map(c => HttpSearchCursor(c.startAt, c.maxResults))
//                      ))
//                }
//              }
//              r <- Ok(body)
//            } yield r
//        }
//
//        "Gets an absence by id" **
//          "absence" @@
//            GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") |>> { (id: AbsenceId) =>
//          for {
//            absence       <- absenceRepo.unsafeGet(id)
//            absenceReason <- absenceReasonRepo.unsafeGet(absence.reasonId)
//            r             <- Ok(httpAbsenceFrom(absence, absenceReason))
//          } yield r
//        }
//      }

      private def httpAbsenceFrom(domain: Absence, absenceReason: AbsenceReason): HttpV0Absence = HttpV0Absence(
        id = domain.absenceId.asString,
        from = domain.from,
        daysQuantity = domain.daysQuantity,
        reason = absenceReason.name
      )
    }
  }
}
