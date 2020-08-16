package com.github.vsuharnikov.barbarissa.backend

import java.io.{PrintWriter, StringWriter}
import java.security.Security
import java.time.ZoneId
import java.util.Properties

import cats.ApplicativeError
import cats.SemigroupK.ops.toAllSemigroupKOps
import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.absence.app.AbsenceHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.absence.domain.{AbsenceReasonRepo, AbsenceRepo}
import com.github.vsuharnikov.barbarissa.backend.absence.infra.ConfigurableAbsenceReasonRepo
import com.github.vsuharnikov.barbarissa.backend.absence.infra.jira.JiraAbsenceRepo
import com.github.vsuharnikov.barbarissa.backend.appointment.app.AppointmentHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AbsenceAppointmentService
import com.github.vsuharnikov.barbarissa.backend.appointment.infra.exchange.MsExchangeAbsenceAppointmentService
import com.github.vsuharnikov.barbarissa.backend.employee.app.{EmployeeHttpApiRoutes, requestIdLogAnnotation}
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.employee.infra._
import com.github.vsuharnikov.barbarissa.backend.employee.infra.db.{DbCachedEmployeeRepo, DbMigrationRepo}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.JiraEmployeeRepo
import com.github.vsuharnikov.barbarissa.backend.queue.infra.db.DbAbsenceQueueRepo
import com.github.vsuharnikov.barbarissa.backend.shared.app.ApiError
import com.github.vsuharnikov.barbarissa.backend.shared.domain.ReportService
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.{DbTransactor, SqliteDataSource}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange.{MsExchangeMailService, MsExchangeService}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import com.github.vsuharnikov.barbarissa.backend.shared.infra.{DocxReportService, PadegInflection}
import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
import org.http4s.Status.InternalServerError
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import org.http4s.implicits._
import org.http4s.rho.bits.PathAST.{PathMatch, TypedPath}
import org.http4s.rho.swagger.models.{ArrayModel, Info, RefProperty, Tag}
import org.http4s.rho.swagger.{SwaggerSupport, TypeBuilder, models}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{RequestId, ResponseLogger}
import org.http4s.util.CaseInsensitiveString
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
                           jira: JiraApi.Config,
                           msExchange: MsExchangeService.Config,
                           msExchangeAppointment: MsExchangeAbsenceAppointmentService.Config,
                           msExchangeMail: MsExchangeMailService.Config,
                           absenceReasons: ConfigurableAbsenceReasonRepo.Config,
                           sqlite: SqliteDataSource.Config,
                           processing: ProcessingService.Config)
  case class HttpApiConfig(host: String, port: Int)

  // prevents java from caching successful name resolutions, which is needed e.g. for proper NTP server rotation
  // http://stackoverflow.com/a/17219327
  java.lang.System.setProperty("sun.net.inetaddr.ttl", "0")
  java.lang.System.setProperty("sun.net.inetaddr.negative.ttl", "0")
  Security.setProperty("networkaddress.cache.ttl", "0")
  Security.setProperty("networkaddress.cache.negative.ttl", "0")

  private type AppEnvironment = Clock
    with Has[HttpApiConfig]
    with Logging
    with EmployeeRepo
    with AbsenceRepo
    with AbsenceReasonRepo
    with MigrationRepo
    with ReportService
    with AbsenceAppointmentService
    with ProcessingService
    with AbsenceQueue

  private type AppTask[A]  = RIO[AppEnvironment, A]
  private type UAppTask[A] = URIO[AppEnvironment, A]

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
                read(descriptor[GlobalConfig].mapKey(toKebabCase) from source).left.map(_.prettyPrint())
              }
          }
          .mapError(desc => new IllegalArgumentException(s"Can't parse config:\n$desc"))
      }

    val loggingLayer = Slf4jLogger.makeWithAnnotationsAsMdc(
      rootLoggerName = Some("barbarissa-main"),
      logFormat = (_, logEntry) => logEntry,
      mdcAnnotations = List(requestIdLogAnnotation)
    )

    val httpClientLayer = loggingLayer ++ httpClient.toLayer >>> {
      val loggerNameBase   = "org.http4s.client.middleware.Logger".split('.').toList
      val loggerAnnotation = LogAnnotation.Name(loggerNameBase)
      ZIO
        .access[Has[Client[Task]] with Logging] { env =>
          val client = env.get[Client[Task]]
          Logger[Task](
            logBody = true,
            logHeaders = true,
            logAction = ((x: String) => log.locally(loggerAnnotation) { log.debug(x) }.provide(env)).some
          )(client)
        }
        .toLayer
    }

    val dataSourceLayer = configLayer.narrow(_.barbarissa.backend.sqlite) ++ Blocking.live ++ loggingLayer >>> SqliteDataSource.live

    val transactorLayer = dataSourceLayer ++ Blocking.live ++ loggingLayer >>> DbTransactor.live

    val migrationRepoLayer = transactorLayer >>> DbMigrationRepo.live

    val jiraApiLayer = configLayer.narrow(_.barbarissa.backend.jira) ++ Clock.live ++ loggingLayer ++ httpClientLayer >>>
      JiraApi.live

    val employeeRepoLayer = {
      val underlyingLayer = loggingLayer ++ jiraApiLayer >>> JiraEmployeeRepo.live
      loggingLayer ++ transactorLayer ++ migrationRepoLayer ++ underlyingLayer >>> DbCachedEmployeeRepo.live
    }

    val absenceRepoLayer = loggingLayer ++ jiraApiLayer >>> JiraAbsenceRepo.live

    val msExchangeServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchange) ++ Blocking.live ++ loggingLayer >>>
      MsExchangeService.live

    val absenceAppointmentServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchangeAppointment) ++ Clock.live ++
      Blocking.live ++ msExchangeServiceLayer >>> MsExchangeAbsenceAppointmentService.live

    val absenceReasonRepoLayer = configLayer.narrow(_.barbarissa.backend.absenceReasons) >>> ConfigurableAbsenceReasonRepo.live

    val absenceQueueLayer = transactorLayer ++ migrationRepoLayer >>> DbAbsenceQueueRepo.live

    val reportServiceLayer = DocxReportService.live

    val mailServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchangeMail) ++ Clock.live ++ Blocking.live ++
      loggingLayer ++ msExchangeServiceLayer >>> MsExchangeMailService.live

    val processingServiceLayer = configLayer.narrow(_.barbarissa.backend.processing) ++ Clock.live ++ loggingLayer ++
      employeeRepoLayer ++ absenceRepoLayer ++ absenceReasonRepoLayer ++ absenceQueueLayer ++ absenceAppointmentServiceLayer ++
      reportServiceLayer ++ mailServiceLayer >>> ProcessingService.live

    val routesLayer = loggingLayer ++ employeeRepoLayer ++
      absenceRepoLayer ++ absenceReasonRepoLayer ++ absenceQueueLayer ++ reportServiceLayer ++ absenceAppointmentServiceLayer ++
      processingServiceLayer

    val appLayer: ZLayer[ZEnv, Throwable, AppEnvironment] =
      Clock.live ++ loggingLayer ++ configLayer.narrow(_.barbarissa.backend.httpApi) ++
        employeeRepoLayer ++ absenceRepoLayer ++
        absenceAppointmentServiceLayer ++ absenceReasonRepoLayer ++ reportServiceLayer ++ migrationRepoLayer ++
        processingServiceLayer ++ routesLayer

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

  private def makeHttpServer: ZIO[AppEnvironment, Throwable, Unit] =
    ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
      val s = SwaggerSupport[AppTask]
      import s._

      val rhoMiddleware = createRhoMiddleware(
        apiInfo = Info(title = "Barbarissa Backend", version = Version.VersionString),
        apiPath = TypedPath(PathMatch("api")) / "docs" / "swagger.json",
        //          swaggerFormats = DefaultSwaggerFormats
        //            .withSerializers(typeOf[ApiV0Countries], mkSwaggerArrayModel[ApiV0Countries, ApiV0Country])
        //            .withSerializers(typeOf[ApiV0Cities], mkSwaggerArrayModel[ApiV0Cities, ApiV0City]),
        tags = List(
          Tag(name = "employee"),
          Tag(name = "absence"),
          Tag(name = "appointment"),
          Tag(name = "queue"),
          Tag(name = "processing"),
        )
      )

      val log = rts.environment.get[Logger[String]]

      // TODO AppEnvironment layer? huh
      val redactHeadersWhen        = Headers.SensitiveHeaders.contains _
      val loggerNameBase           = "org.http4s.server.middleware".split('.').toList // HACK
      val requestLoggerAnnotation  = LogAnnotation.Name(loggerNameBase ::: "RequestLogger" :: Nil)
      val responseLoggerAnnotation = LogAnnotation.Name(loggerNameBase ::: "ResponseLogger" :: Nil)

      val routes = {
        val routes = new EmployeeHttpApiRoutes[AppEnvironment](PadegInflection).rhoRoutes.toRoutes(rhoMiddleware) <+>
          new AbsenceHttpApiRoutes[AppEnvironment].rhoRoutes.toRoutes(rhoMiddleware) <+>
          new AppointmentHttpApiRoutes[AppEnvironment].rhoRoutes.toRoutes(rhoMiddleware)

        val withResponseLogging = catchErrors(routes)

        ResponseLogger.httpRoutes[AppTask, Request[AppTask]](
          logHeaders = true,
          logBody = false,
          logAction = ((x: String) => log.locally(responseLoggerAnnotation) { log.debug(x) }).some,
          redactHeadersWhen = redactHeadersWhen
        )(withResponseLogging)
      }

      val httpApp: Kleisli[AppTask, Request[AppTask], Response[AppTask]] = {
        val raw = Router[AppTask]("/" -> routes).orNotFound
        val withRequestLogging = Kleisli { req: Request[AppTask] =>
          val requestId = req.headers.get(CaseInsensitiveString("X-Request-ID")).fold("null")(_.value)
          log.locally(_.annotate(requestIdLogAnnotation, requestId)) {
            for {
              // TODO Why the standard RequestLogger does not receive RequestId? Probably Concurrent is a root cause
              _ <- org.http4s.internal.Logger.logMessage[AppTask, Request[AppTask]](req)(
                logHeaders = true,
                logBody = true,
                redactHeadersWhen
              ) { x =>
                log.locally(requestLoggerAnnotation) {
                  log.debug(x)
                }
              }
              res <- raw(req)
            } yield res
          }
        }
        RequestId.httpApp[AppTask](raw)
//        RequestId.httpApp[AppTask](withRequestLogging) // Issue with multipart
      }

      val restApiConfig = rts.environment.get[HttpApiConfig]
      BlazeServerBuilder[AppTask](rts.platform.executor.asEC)
        .bindHttp(restApiConfig.port, restApiConfig.host)
        .withHttpApp(httpApp)
        .serve
        .compile[AppTask, AppTask, cats.effect.ExitCode]
        .drain
    }

  // TODO refactor
  object WrapMiddleware {
    def apply[F[_], G[_]](http: Http[F, G])(wrap: (Request[G], () => F[Response[G]]) => F[Response[G]]): Http[F, G] =
      Kleisli { req: Request[G] =>
        wrap(req, () => http(req))
      }
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
  implicit val uriDescriptor: Descriptor[Uri] = Descriptor[String].xmap(Uri.unsafeFromString, _.renderString)

  implicit def circeJsonDecoder[F[_], A: Decoder](implicit sync: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A]
  implicit def circeJsonEncoder[F[_], A: Encoder]: EntityEncoder[F, A]                         = jsonEncoderOf[F, A]

  private def catchErrors[G[_], F[_], A](http: Kleisli[G, A, Response[F]])(implicit G: ApplicativeError[G, Throwable]): Kleisli[G, A, Response[F]] = {
    Kleisli[G, A, Response[F]] { req =>
      G.recover(http(req)) {
        case x: ApiError => Response[F](x.status).withEntity(x.body)
        case x           => Response[F](InternalServerError).withEntity(Option(x.getMessage).getOrElse(x.getClass.getName))
      }
    }
  }
}
