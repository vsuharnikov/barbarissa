//package com.github.vsuharnikov.barbarissa.backend.api.v0
//
//import cats.data.Kleisli
//import com.github.vsuharnikov.barbarissa.backend.location.app.{ApiV0Cities, ApiV0Countries}
//import com.github.vsuharnikov.barbarissa.backend.services.PdfGenerator
//import com.github.vsuharnikov.cv.backend.BaseSpec
//import com.github.vsuharnikov.cv.backend.api.v0.entities._
//import com.github.vsuharnikov.cv.backend.cv.app.V0RestApiRoutes
//import com.github.vsuharnikov.cv.backend.domain.{City, Country}
//import com.github.vsuharnikov.cv.backend.location.app.{ApiV0Cities, ApiV0City, ApiV0Countries, ApiV0Country}
//import com.github.vsuharnikov.cv.backend.repositories.GeoRepository
//import com.github.vsuharnikov.cv.backend.services.PdfGenerator
//import org.http4s.circe.CirceEntityCodec._
//import org.http4s.implicits._
//import org.http4s.{Method, Request, Response, Status}
//import zio._
//import zio.interop.catz._
//import zio.test.Assertion._
//import zio.test._
//
//import scala.util.{Success, Try}
//
//object V0RestApiRoutesTest extends BaseSpec {
//  type AppEnvironment = Environment with GeoRepository with PdfGenerator
//  type RoutesIO[A]    = RIO[AppEnvironment, A]
//  type Routes         = Kleisli[RoutesIO, Request[RoutesIO], Response[RoutesIO]]
//
//  override def spec =
//    suite("V0RestApiRoutes")(
//      suite("GET /api/v0/countries")(
//        myTestM("sunny day") { routes =>
//          for {
//            response <- routes.run(Request(Method.GET, uri"/api/v0/countries"))
//            entity   <- response.as[ApiV0Countries]
//          } yield {
//            assert(response.status)(equalTo(Status.Ok)) &&
//            assert(entity)(equalTo(ApiV0Countries(List(ApiV0Country("XX", "X-Men land")))))
//          }
//        }
//      ),
//      suite("GET /api/v0/countries/XX/cities")(
//        myTestM("sunny day") { routes =>
//          for {
//            response <- routes.run(Request(Method.GET, uri"/api/v0/countries/XX/cities"))
//            entity   <- response.as[ApiV0Cities]
//          } yield {
//            assert(response.status)(equalTo(Status.Ok)) &&
//            assert(entity)(matchTo(ApiV0Cities(List(ApiV0City(2, "X-Men city")))))
//          }
//        }
//      ),
//      suite("POST /api/v0/cv")(
//        myTestM("sunny day") { routes =>
//          for {
//            response <- routes.run {
//              Request(Method.POST, uri"/api/v0/cv")
//                .withEntity(ApiV0CurriculumVitae("Foo", "Bar", 2, "+7 925 123 12 12", "some@example.com"))
//            }
//            entity <- {
//              import org.http4s.EntityDecoder.byteArrayDecoder
//              response.as[Array[Byte]]
//            }
//          } yield {
//            assert(response.status)(equalTo(Status.Ok)) &&
//            assert(entity)(equalTo(Array[Byte](1, 2, 3)))
//          }
//        }
//      )
//    ).provideCustomLayerShared(
//      mkLayer(
//        GeoRepository.Inline.Config(
//          countries = List(Country("XX", "X-Men land")),
//          cities = List(City(2, "XX", "X-Men city"))
//        ),
//        pdfResult = Success(Array[Byte](1, 2, 3))
//      ))
//
//  private def myTestM(label: String)(assertion: Routes => ZIO[AppEnvironment, Throwable, TestResult]): ZSpec[AppEnvironment, Throwable] =
//    testM(label) {
//      val io = new V0RestApiRoutes[AppEnvironment]
//      assertion(io.rhoRoutes.toRoutes().orNotFound)
//    }
//
//  private def mkLayer(geoConfig: GeoRepository.Inline.Config = GeoRepository.Inline.Config.empty,
//                      pdfResult: Try[Array[Byte]] = Success(Array.emptyByteArray)) =
//    ZLayer.succeed(geoConfig) >>> GeoRepository.Inline.live ++ PdfGenerator.const(pdfResult)
//}
