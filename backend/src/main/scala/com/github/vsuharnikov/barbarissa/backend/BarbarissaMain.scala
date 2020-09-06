package com.github.vsuharnikov.barbarissa.backend

import java.io.{PrintWriter, StringWriter}
import java.security.Security
import java.time.ZoneId
import java.util.Properties

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits.toSemigroupKOps
import cats.syntax.option._
import com.github.vsuharnikov.barbarissa.backend.absence.app.AbsenceHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.absence.infra.ConfigurableAbsenceReasonRepo
import com.github.vsuharnikov.barbarissa.backend.absence.infra.jira.JiraAbsenceRepo
import com.github.vsuharnikov.barbarissa.backend.appointment.app.AppointmentHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.appointment.infra.exchange.MsExchangeAppointmentService
import com.github.vsuharnikov.barbarissa.backend.employee.app.{EmployeeHttpApiRoutes, requestIdLogAnnotation}
import com.github.vsuharnikov.barbarissa.backend.employee.infra.db.DbCachedEmployeeRepo
import com.github.vsuharnikov.barbarissa.backend.employee.infra.jira.JiraEmployeeRepo
import com.github.vsuharnikov.barbarissa.backend.processing.app.ProcessingHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.processing.infra.ProcessingService
import com.github.vsuharnikov.barbarissa.backend.queue.app.QueueHttpApiRoutes
import com.github.vsuharnikov.barbarissa.backend.queue.infra.db.DbAbsenceQueueRepo
import com.github.vsuharnikov.barbarissa.backend.shared.infra.DocxReportService
import com.github.vsuharnikov.barbarissa.backend.shared.infra.db.{DbMigrationRepo, H2DbTransactor}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange.{MsExchangeMailService, MsExchangeService}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.jira.JiraApi
import com.github.vsuharnikov.barbarissa.backend.swagger.app.SwaggerHttpApiRoutes
import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
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
import shapeless.HNil
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.circe.yaml._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor.{Descriptor, descriptor}
import zio.config.syntax._
import zio.config.typesafe.TypesafeConfigSource
import zio.console.putStrLn
import zio.interop.catz._
import zio.interop.catz.implicits._
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
                           msExchangeAppointment: MsExchangeAppointmentService.Config,
                           msExchangeMail: MsExchangeMailService.Config,
                           absenceReasons: ConfigurableAbsenceReasonRepo.Config,
                           h2: H2DbTransactor.Config,
                           processing: ProcessingService.Config)
  case class HttpApiConfig(host: String, port: Int)

  // prevents java from caching successful name resolutions, which is needed e.g. for proper NTP server rotation
  // http://stackoverflow.com/a/17219327
  java.lang.System.setProperty("sun.net.inetaddr.ttl", "0")
  java.lang.System.setProperty("sun.net.inetaddr.negative.ttl", "0")
  Security.setProperty("networkaddress.cache.ttl", "0")
  Security.setProperty("networkaddress.cache.negative.ttl", "0")

  private type AppEnvironment = Clock
    with Blocking
    with Has[HttpApiConfig]
    with Logging
    with AbsenceHttpApiRoutes
    with AppointmentHttpApiRoutes
    with EmployeeHttpApiRoutes
    with ProcessingHttpApiRoutes
    with QueueHttpApiRoutes

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
    val configLayer = ZIO
      .fromEither {
        val typesafeConfig = ConfigFactory.defaultApplication().resolve()
        TypesafeConfigSource
          .fromTypesafeConfig(typesafeConfig)
          .flatMap { source =>
            read(descriptor[GlobalConfig].mapKey(toKebabCase) from source).left.map(_.prettyPrint())
          }
      }
      .mapError(desc => new IllegalArgumentException(s"Can't parse config:\n$desc"))
      .toLayer

    val loggingLayer = Slf4jLogger.makeWithAnnotationsAsMdc(
      logFormat = (_, logEntry) => logEntry,
      mdcAnnotations = List(requestIdLogAnnotation)
    ) >>> Logging.withRootLoggerName("barbarissa")

    val httpClientLayer = loggingLayer ++ httpClient.toLayer >>> {
      val loggerNameBase   = "org.http4s.client.middleware.Logger".split('.').toList
      val loggerAnnotation = LogAnnotation.Name(loggerNameBase)
      ZIO
        .access[Has[Client[Task]] with Logging] { env =>
          val client = env.get[Client[Task]]
          Logger[Task](
            logBody = false,
            logHeaders = false,
            logAction = ((x: String) => log.locally(loggerAnnotation) { log.debug(x) }.provide(env)).some
          )(client)
        }
        .toLayer
    }

    val transactorLayer = configLayer.narrow(_.barbarissa.backend.h2) >>> H2DbTransactor.live

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

    val appointmentServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchangeAppointment) ++ Clock.live ++
      Blocking.live ++ msExchangeServiceLayer >>> MsExchangeAppointmentService.live

    val absenceReasonRepoLayer = configLayer.narrow(_.barbarissa.backend.absenceReasons) >>> ConfigurableAbsenceReasonRepo.live

    val queueLayer = transactorLayer ++ migrationRepoLayer >>> DbAbsenceQueueRepo.live

    val reportServiceLayer = DocxReportService.live

    val mailServiceLayer = configLayer.narrow(_.barbarissa.backend.msExchangeMail) ++ Clock.live ++ Blocking.live ++
      loggingLayer ++ msExchangeServiceLayer >>> MsExchangeMailService.live

    val processingServiceLayer = configLayer.narrow(_.barbarissa.backend.processing) ++ Clock.live ++ loggingLayer ++
      employeeRepoLayer ++ absenceRepoLayer ++ absenceReasonRepoLayer ++ queueLayer ++ appointmentServiceLayer ++
      reportServiceLayer ++ mailServiceLayer >>> ProcessingService.live

    val absenceHttpLayer     = absenceRepoLayer ++ absenceReasonRepoLayer >>> AbsenceHttpApiRoutes.live
    val appointmentHttpLayer = employeeRepoLayer ++ absenceRepoLayer ++ appointmentServiceLayer >>> AppointmentHttpApiRoutes.live
    val employeeHttpLayer    = loggingLayer ++ employeeRepoLayer >>> EmployeeHttpApiRoutes.live
    val processingHttpLayer  = processingServiceLayer >>> ProcessingHttpApiRoutes.live
    val queueHttpLayer       = queueLayer >>> QueueHttpApiRoutes.live
//    val swaggerHttpLayer     = Blocking.live >>> SwaggerHttpApiRoutes.live

    val appLayer: ZLayer[ZEnv, Throwable, AppEnvironment] =
      Clock.live ++
        Blocking.live ++
        configLayer.narrow(_.barbarissa.backend.httpApi) ++
        loggingLayer ++
        absenceHttpLayer ++
        appointmentHttpLayer ++
        employeeHttpLayer ++
        processingHttpLayer ++
        queueHttpLayer /*++
        swaggerHttpLayer*/

    // TODO ProcessingService.start
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
      val s = SwaggerSupport[Task]
      import s._

      val rhoMiddleware = createRhoMiddleware(
        apiInfo = Info(title = "Barbarissa Backend", version = Version.VersionString),
        apiPath = TypedPath[Task, HNil](PathMatch("api")) / "docs" / "swagger.json",
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
      val blocking                 = rts.environment.get[Blocking.Service]

      val (routes, yaml) = {
        val routes = List(
          rts.environment.get[EmployeeHttpApiRoutes.Service],
          rts.environment.get[AbsenceHttpApiRoutes.Service],
          rts.environment.get[AppointmentHttpApiRoutes.Service],
          rts.environment.get[ProcessingHttpApiRoutes.Service],
          rts.environment.get[QueueHttpApiRoutes.Service]
        )
        val http4sRoutes = routes.map(_.http4sRoute).reduceLeft(_ <+> _)
        val yaml = routes
          .map(_.openApiDoc)
          .reduceLeft[OpenAPI] {
            case (x, r) =>
              r.copy(
                tags = x.tags ::: r.tags,
                paths = x.paths ++ r.paths,
                components = (x.components, r.components) match {
                  case (None, x) => x
                  case (x, None) => x
                  case (Some(x), Some(r)) =>
                    Some(
                      r.copy(
                        schemas = r.schemas ++ x.schemas,
                        securitySchemes = r.securitySchemes ++ x.securitySchemes
                      ))
                },
                security = x.security ++ r.security
              )
          }
          .toYaml

        // val withCatchingErrors = catchErrors(routes)

        ResponseLogger.httpRoutes[Task, Request[Task]](
          logHeaders = true,
          logBody = false,
          logAction = ((x: String) => log.locally(responseLoggerAnnotation) { log.debug(x) }).some,
          redactHeadersWhen = redactHeadersWhen
        )(http4sRoutes) -> yaml
      }

      val httpApp: Kleisli[Task, Request[Task], Response[Task]] = {
        val raw = Router[Task]("/" -> (routes <+> SwaggerHttpApiRoutes.live(blocking.blockingExecutor, yaml).routes)).orNotFound
        val withRequestLogging = Kleisli { req: Request[Task] =>
          val requestId = req.headers.get(CaseInsensitiveString("X-Request-ID")).fold("null")(_.value)
          log.locally(_.annotate(requestIdLogAnnotation, requestId)) {
            for {
              // TODO Why the standard RequestLogger does not receive RequestId? Probably Concurrent is a root cause
              _ <- org.http4s.internal.Logger.logMessage[Task, Request[Task]](req)(
                logHeaders = true,
                logBody = false, // Prevents working of batch updates, because consumes body
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

        // Issue with multipart
        val finalRoutes = withRequestLogging
        RequestId.httpApp[Task](finalRoutes)
      }

      val restApiConfig = rts.environment.get[HttpApiConfig]
      BlazeServerBuilder[Task](rts.platform.executor.asEC)
        .bindHttp(restApiConfig.port, restApiConfig.host)
        .withHttpApp(httpApp)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
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
  implicit val uriDescriptor: Descriptor[Uri] = Descriptor[String].xmap(Uri.unsafeFromString, _.renderString)

  implicit def circeJsonDecoder[F[_], A: Decoder](implicit sync: Sync[F]): EntityDecoder[F, A] = jsonOf[F, A]
  implicit def circeJsonEncoder[F[_], A: Encoder]: EntityEncoder[F, A]                         = jsonEncoderOf[F, A]
}
