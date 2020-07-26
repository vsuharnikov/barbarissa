package com.github.vsuharnikov.barbarissa.backend

import java.io.{PrintWriter, StringWriter}
import java.security.Security
import java.time.ZoneId
import java.util.Properties

import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.employee.app.EmployeeHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.{AbsenceJiraRepo, EmployeeJiraRepo}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.ReportService
import com.github.vsuharnikov.barbarissa.backend.shared.infra.{DocxReportService, PadegInflection, SqliteDataSource}
import com.typesafe.config.ConfigFactory
import io.github.gaelrenoux.tranzactio.doobie.Database
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import org.http4s.implicits._
import org.http4s.rho.bits.PathAST.{PathMatch, TypedPath}
import org.http4s.rho.swagger.models.{ArrayModel, Info, RefProperty, Tag}
import org.http4s.rho.swagger.{SwaggerSupport, TypeBuilder, models}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor.{Descriptor, descriptor}
import zio.config.syntax._
import zio.config.typesafe.TypesafeConfigSource
import zio.console.putStrLn
import zio.interop.catz._
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.{Tag => _, TypeTag => _, config => _, _}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object BarbarissaMain extends App {
  case class GlobalConfig(barbarissa: BarbarissaConfig)
  case class BarbarissaConfig(backend: BackendConfig)
  case class BackendConfig(httpApi: HttpApiConfig,
                           jira: EmployeeJiraRepo.Config,
                           msExchange: MsExchangeAbsenceAppointmentService.Config,
                           routes: EmployeeHttpApiRoutes.Config,
                           absenceReasons: ConfigurableAbsenceReasonRepo.Config,
                           sqlite: SqliteDataSource.Config)
  case class HttpApiConfig(host: String, port: Int)

  // prevents java from caching successful name resolutions, which is needed e.g. for proper NTP server rotation
  // http://stackoverflow.com/a/17219327
  java.lang.System.setProperty("sun.net.inetaddr.ttl", "0")
  java.lang.System.setProperty("sun.net.inetaddr.negative.ttl", "0")
  Security.setProperty("networkaddress.cache.ttl", "0")
  Security.setProperty("networkaddress.cache.negative.ttl", "0")

  private type AppEnvironment = Clock
    with Has[HttpApiConfig]
    with Has[EmployeeHttpApiRoutes.Config]
    with Logging
    with EmployeeRepo
    with AbsenceRepo
    with AbsenceReasonRepo
    with VersionRepo
    with ReportService
    with AbsenceAppointmentService

  private type AppTask[A] = RIO[AppEnvironment, A]

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val program = makeHttpClient.flatMap(makeProgram)

    program.foldM(
      e => putStrLn(showWithStackTrace(e)) *> ZIO.succeed(ExitCode.failure),
      _ => ZIO.succeed(ExitCode.success)
    )
  }

  private def makeProgram(httpClient: TaskManaged[Client[Task]]): RIO[ZEnv, Unit] = {
    val configLayer = ZLayer
      .fromEffect {
        ZIO
          .fromEither {
            val typesafeConfig = ConfigFactory.defaultApplication().resolve()
            TypesafeConfigSource
              .fromTypesafeConfig(typesafeConfig)
              .flatMap { source =>
                read(descriptor[GlobalConfig].mapKey(camelToKebab) from source).left.map(_.prettyPrint())
              }
          }
          .mapError(desc => new IllegalArgumentException(s"Can't parse config:\n$desc"))
      }

    val loggingLayer = Slf4jLogger.make(
      logFormat = (_, logEntry) => logEntry,
      rootLoggerName = Some("barbarissa-main")
    )

    val httpClientLayer = httpClient.map(Logger[Task](logBody = true, logHeaders = true)(_)).toLayer

    val employeeJiraRepositoryLayer = configLayer.narrow(_.barbarissa.backend.jira) ++ httpClientLayer >>> EmployeeJiraRepo.live

    val absenceJiraRepositoryLayer = configLayer.narrow(_.barbarissa.backend.jira) ++ httpClientLayer >>> AbsenceJiraRepo.live

    val absenceAppointmentServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchange) >>> MsExchangeAbsenceAppointmentService.live

    val absenceReasonRepoLayer = configLayer.narrow(_.barbarissa.backend.absenceReasons) >>> ConfigurableAbsenceReasonRepo.live

    val dataSourceLayer = Blocking.live ++ configLayer.narrow(_.barbarissa.backend.sqlite) >>> SqliteDataSource.live

    val databaseLayer = Blocking.live ++ Clock.live ++ dataSourceLayer >>> Database.fromDatasource

    val versionRepoLayer = databaseLayer >>> DbVersionRepo.live

    val appLayer: ZLayer[ZEnv, Throwable, AppEnvironment] =
      Clock.live ++ loggingLayer ++ configLayer.narrow(_.barbarissa.backend.httpApi) ++
        configLayer.narrow(_.barbarissa.backend.routes) ++ employeeJiraRepositoryLayer ++ absenceJiraRepositoryLayer ++
        absenceAppointmentServiceLayer ++ absenceReasonRepoLayer ++ DocxReportService.live ++ versionRepoLayer

    val app: ZIO[AppEnvironment, Throwable, Unit] = for {
      _             <- log.info("Loading config")
      restApiConfig <- config[HttpApiConfig]
      _             <- log.info(s"Binding REST API to ${restApiConfig.host}:${restApiConfig.port}")
      _             <- makeHttpServer
    } yield ()

    app.provideSomeLayer(appLayer)
  }

  private def makeHttpClient =
    ZIO
      .runtime[Any]
      .map { implicit rts =>
        BlazeClientBuilder
          .apply[Task](rts.platform.executor.asEC)
          .resource
          .toManaged
      }

  private def makeHttpServer =
    ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
      val s = SwaggerSupport[AppTask]
      import s._

      val middleware = createRhoMiddleware(
        apiInfo = Info(title = "Barbarissa Backend", version = Version.VersionString),
        apiPath = TypedPath(PathMatch("api")) / "docs" / "swagger.json",
        //          swaggerFormats = DefaultSwaggerFormats
        //            .withSerializers(typeOf[ApiV0Countries], mkSwaggerArrayModel[ApiV0Countries, ApiV0Country])
        //            .withSerializers(typeOf[ApiV0Cities], mkSwaggerArrayModel[ApiV0Cities, ApiV0City]),
        tags = List(
          Tag(name = "employee"),
          Tag(name = "absence"),
          Tag(name = "appointment"),
        )
      )

      val httpApp = Router[AppTask]("/" -> new EmployeeHttpApiRoutes(PadegInflection).rhoRoutes.toRoutes(middleware)).orNotFound

      val restApiConfig = rts.environment.get[HttpApiConfig]
      BlazeServerBuilder[AppTask](rts.platform.executor.asEC)
        .bindHttp(restApiConfig.port, restApiConfig.host)
        .withHttpApp(httpApp)
        .serve
        .compile[AppTask, AppTask, cats.effect.ExitCode]
        .drain
    }

  private def showWithStackTrace(x: Throwable): String = {
    val sw = new StringWriter
    x.printStackTrace(new PrintWriter(sw))
    s"$x\n${sw.toString}"
  }

  private def mkSwaggerArrayModel[ArrayT, ItemT: TypeTag](implicit act: ClassTag[ArrayT], ict: ClassTag[ItemT]): Set[models.Model] = {
    val className = act.runtimeClass.getSimpleName
    mkSwaggerModel[ItemT] + ArrayModel(
      id = className,
      id2 = className,
      `type` = "array".some,
      items = RefProperty(ref = ict.runtimeClass.getSimpleName).some
    )
  }

  private def mkSwaggerModel[T](implicit tt: TypeTag[T]) =
    TypeBuilder.collectModels(tt.tpe, Set.empty, org.http4s.rho.swagger.DefaultSwaggerFormats, typeOf[AppTask[_]])

  implicit val zoneIdDescriptor: Descriptor[ZoneId] = Descriptor[String].xmap(ZoneId.of, _.getId)
  implicit val propertiesDescriptor: Descriptor[Properties] = Descriptor[Map[String, String]].xmap(
    { raw =>
      val p = new Properties()
      raw.foreach { case (k, v) => p.put(k, v) }
      p
    },
    _.asScala.map { case (k, v) => s"$k" -> s"$v" }.toMap
  )
}
