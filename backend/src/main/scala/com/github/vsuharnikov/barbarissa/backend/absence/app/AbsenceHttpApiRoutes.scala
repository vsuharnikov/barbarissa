package com.github.vsuharnikov.barbarissa.backend.absence.app

import com.github.vsuharnikov.barbarissa.backend.absence.app.entities.HttpV0Absence
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.app._
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSupport
import zio.interop.catz._
import zio.{RIO, URIO, ZIO}

class AbsenceHttpApiRoutes[R <: AbsenceRepo with AbsenceReasonRepo] extends JsonEntitiesEncoding[RIO[R, *]] {
  type HttpIO[A]   = RIO[R, A]
  type HttpURIO[A] = URIO[R, A]

  private val swaggerSyntax = new SwaggerSupport[HttpIO] {}
  import swaggerSyntax._

  val rhoRoutes: RhoRoutes[HttpIO] = new RhoRoutes[HttpIO] {
    val parsers = new RoutesParsers[HttpIO]()
    import parsers._

    "Gets an employee's absences" **
      "absence" @@
        GET / "api" / "v0" / "employee" / pathVar[EmployeeId]("id") / "absence" +? param[Option[HttpSearchCursor]]("cursor") |>> {
      (id: EmployeeId, cursor: Option[HttpSearchCursor]) =>
        for {
          absences       <- AbsenceRepo.getByCursor(AbsenceRepo.GetCursor(id, cursor.fold(0)(_.startAt), cursor.fold(0)(_.maxResults)))
          absenceReasons <- AbsenceReasonRepo.all
          body <- {
            val (as, nextCursor) = absences
            import cats.implicits._
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
          r <- Ok(body)
        } yield r
    }

    "Gets an absence by id" **
      "absence" @@
        GET / "api" / "v0" / "absence" / pathVar[AbsenceId]("absenceId") |>> { (id: AbsenceId) =>
      for {
        absence <- AbsenceRepo.get(id).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Absence", id.asString)))
        }
        absenceReason <- AbsenceReasonRepo.get(absence.reasonId).flatMap {
          case Some(x) => ZIO.succeed(x)
          case None    => ZIO.fail(ApiError.from(DomainError.NotFound("Absence", id.asString)))
        }
        r <- Ok(httpAbsenceFrom(absence, absenceReason))
      } yield r
    }
  }

  private def httpAbsenceFrom(domain: Absence, absenceReason: AbsenceReason): HttpV0Absence = HttpV0Absence(
    id = domain.absenceId.asString,
    from = domain.from,
    daysQuantity = domain.daysQuantity,
    reason = absenceReason.name
  )
}
