package com.github.vsuharnikov.barbarissa.backend.processing.infra

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import com.github.vsuharnikov.barbarissa.backend.absence.domain.AbsenceRepo.GetAfterCursor
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{Appointment, AppointmentService}
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.MailService.EmailAddress
import com.github.vsuharnikov.barbarissa.backend.shared.domain._
import com.github.vsuharnikov.barbarissa.backend.shared.infra.PadegInflection
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.{LogAnnotation, Logger, Logging}
import zio.macros.accessible

@accessible
object ProcessingService {
  trait Service {
    def start: Task[Unit]
    def refreshQueue: Task[Unit]
    def processQueue: Task[Unit]
    def createClaim(aid: AbsenceId): Task[Array[Byte]]
  }

  case class Config(autoStart: Boolean,
                    processAfterAbsenceId: Option[AbsenceId],
                    templatesDir: File,
                    refreshQueueInterval: Duration,
                    processQueueInterval: Duration)

  type Dependencies = Has[Config]
    with Clock
    with Logging
    with EmployeeRepo
    with AbsenceRepo
    with AbsenceReasonRepo
    with AbsenceQueue
    with AppointmentService
    with ReportService
    with MailService

  private val locale        = Locale.forLanguageTag("ru")
  private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(new Locale("ru"))

  val live = ZIO
    .accessM[Dependencies] { env =>
      val config             = env.get[Config]
      val log                = env.get[Logger[String]]
      val absenceRepo        = env.get[AbsenceRepo.Service]
      val absenceQueue       = env.get[AbsenceQueue.Service]
      val absenceReasonRepo  = env.get[AbsenceReasonRepo.Service]
      val employeeRepo       = env.get[EmployeeRepo.Service]
      val appointmentService = env.get[AppointmentService.Service]
      val reportService      = env.get[ReportService.Service]
      val mailService        = env.get[MailService.Service]

      val service: Service = new Service {
        override def start: UIO[Unit] = {
          val refresh = refreshQueue.ignore.repeat(Schedule.spaced(config.refreshQueueInterval)).delay(config.refreshQueueInterval)
          val process = processQueue.ignore.repeat(Schedule.spaced(config.processQueueInterval)).delay(config.processQueueInterval)
          refresh.forkDaemon *> process.forkDaemon
        }.provide(env).unit

        override def refreshQueue: Task[Unit] =
          absenceQueue.last(10).flatMap { xs =>
            if (xs.isEmpty) paginatedLoop(GetAfterCursor(None, 0, 10))
            else tryRefreshQueue(xs.map(_.absenceId))
          }

        private def tryRefreshQueue(xs: List[AbsenceId]): Task[Unit] = {
          xs.headOption match {
            case None => Task.fail(DomainError.NotEnoughData("Failed to refresh queue. Try to update process-after-absence-id and restart"))
            case x =>
              paginatedLoop(GetAfterCursor(x, 0, 10)).catchSome {
                case e @ DomainError.JiraError(messages) =>
                  if (messages.exists(_.matches("^An issue with key '.+' does not exist for field 'key'.$"))) tryRefreshQueue(xs.tail)
                  else Task.fail(e)
              }
          }
        }

        override def processQueue: Task[Unit] =
          absenceQueue
            .getUncompleted(10)
            .flatMap { xs =>
              if (xs.isEmpty) ZIO.unit else processMany(xs)
            }

        override def createClaim(aid: AbsenceId): Task[Array[Byte]] =
          for {
            absence       <- absenceRepo.unsafeGet(aid)
            absenceReason <- absenceReasonRepo.unsafeGet(absence.reasonId)
            employee      <- employeeRepo.unsafeGet(absence.employeeId)
            absenceReasonSuffix <- absenceReason.needClaim match {
              case None => ZIO.fail(DomainError.Impossible("The claim is not required"))
              case Some(claimType) =>
                ZIO.succeed(claimType match {
                  case AbsenceClaimType.WithoutCompensation => "without-compensation"
                  case AbsenceClaimType.WithCompensation    => "with-compensation"
                })
            }
            templateFile <- {
              val companyId = employee.companyId.map(_.asString).getOrElse("unknown")
              val fileName  = s"$companyId-$absenceReasonSuffix.docx"
              val r         = config.templatesDir.toPath.resolve(fileName).toFile
              if (r.isFile) ZIO.succeed(r)
              else ZIO.fail(DomainError.NotFound("Template", fileName))
            }
            claim  <- absenceClaimRequestFrom(PadegInflection, employee, absence)
            report <- reportService.generate(templateFile, ToArgs.toArgs(claim).toMap)
          } yield report

        private def processMany(xs: List[AbsenceQueueItem]): Task[Unit] = Task.foreach(xs)(processOne(_).ignore).unit

        private def processOne(x: AbsenceQueueItem): Task[Unit] =
          log.locallyAnnotate(LogAnnotation.Name, "ProcessingService" :: s"processOne(${x.absenceId})" :: Nil) {
            ZIO
              .when(!x.done) {
                val process = for {
                  absence       <- absenceRepo.unsafeGet(x.absenceId)
                  absenceReason <- absenceReasonRepo.unsafeGet(absence.reasonId)
                  employee      <- employeeRepo.unsafeGet(absence.employeeId)
                  appointmentCreated <- ZIO
                    .when(!x.appointmentCreated) {
                      createAppointment(employee, absence, absenceReason)
                    }
                    .foldM(
                      e => log.warn(s"Can't send a claim: ${e.getMessage}") *> ZIO.succeed(false),
                      _ => ZIO.succeed(true)
                    )
                  claimSent <- ZIO
                    .when(!x.claimSent) {
                      sendClaim(employee, absence, absenceReason)
                    }
                    .foldM(
                      e => log.warn(s"Can't send a claim: ${e.getMessage}") *> ZIO.succeed(false),
                      _ => ZIO.succeed(true)
                    )
                  _ <- ZIO.when(appointmentCreated || claimSent) {
                    val draft = x.copy(
                      done = appointmentCreated && claimSent,
                      appointmentCreated = appointmentCreated,
                      claimSent = claimSent,
                      retries = x.retries + 1
                    )
                    absenceQueue.update(draft)
                  }
                } yield ()

                process.tapError { e =>
                  log.warn(s"Can't process ${x.absenceId}\n${e.getMessage}")
                }
              }
          }

        private def sendClaim(employee: Employee, absence: Absence, absenceReason: AbsenceReason): Task[Unit] =
          ZIO
            .foreach(absenceReason.needClaim) { claimType =>
              val absenceReasonSuffix = claimType match {
                case AbsenceClaimType.WithoutCompensation => "without-compensation"
                case AbsenceClaimType.WithCompensation    => "with-compensation"
              }
              for {
                templateFile <- {
                  val companyId = employee.companyId.map(_.asString).getOrElse("unknown")
                  val fileName  = s"$companyId-$absenceReasonSuffix.docx"
                  val r         = config.templatesDir.toPath.resolve(fileName).toFile
                  if (r.isFile) ZIO.succeed(r)
                  else ZIO.fail(DomainError.NotFound("Template", fileName))
                }
                claim  <- absenceClaimRequestFrom(PadegInflection, employee, absence)
                report <- reportService.generate(templateFile, ToArgs.toArgs(claim).toMap)
                _ <- mailService.send(
                  to = EmailAddress(employee.email),
                  subject = s"Заявление: ${absenceReason.name} с ${absence.from}",
                  bodyText = "Подпишите и отправьте в HR",
                  attachments = Map(
                    "claim.docx" -> report
                  )
                )
              } yield ()
            }
            .unit

        private def createAppointment(employee: Employee, absence: Absence, absenceReason: AbsenceReason): Task[Unit] =
          for {
            localizedName <- employee.localizedName match {
              case Some(x) => ZIO.succeed(x)
              case None    => ZIO.fail(DomainError.NotEnoughData(s"Localized name of ${employee.employeeId.asString}"))
            }
            has <- appointmentService.has(
              SearchFilter(
                start = absence.from,
                end = absence.from.plusDays(absence.daysQuantity),
                serviceMark = absence.absenceId.asString
              ))
            _ <- ZIO.when(!has) {
              val absenceAppointment = Appointment(
                subject = s"$localizedName: ${absenceReason.name}",
                description = "",
                startDate = absence.from,
                endDate = absence.from.plusDays(absence.daysQuantity),
                serviceMark = absence.absenceId.asString
              )
              appointmentService.add(absenceAppointment)
            }
          } yield ()

        // TODO ZStream
        private def paginatedLoop(cursor: GetAfterCursor): Task[Unit] =
          for {
            r          <- absenceRepo.getFromByCursor(cursor)
            reasonsMap <- absenceReasonRepo.all
            _ <- {
              val (unprocessed, nextCursor) = r
              val xs = unprocessed.view.flatMap { a =>
                reasonsMap.get(a.reasonId).map(toUnprocessed(a, _))
              }.toList

              if (xs.isEmpty) ZIO.unit
              else absenceQueue.add(xs) *> ZIO.foreach(nextCursor)(paginatedLoop)
            }
          } yield ()
      }

      val zService = ZIO
        .foreach(config.processAfterAbsenceId) { absenceId =>
          absenceQueue.add(
            List(
              AbsenceQueueItem(
                absenceId = absenceId,
                done = true,
                claimSent = false,
                appointmentCreated = false,
                retries = 0
              )))
        }
        .as(service)

      if (config.autoStart) zService.tap(_.start)
      else zService
    }
    .toLayer

  def toUnprocessed(a: Absence, ar: AbsenceReason): AbsenceQueueItem = {
    val claimSent          = ar.needClaim.fold(true)(_ => false)
    val appointmentCreated = ar.needAppointment.fold(true)(!_)
    AbsenceQueueItem(
      absenceId = a.absenceId,
      done = claimSent && appointmentCreated,
      claimSent = claimSent,
      appointmentCreated = appointmentCreated,
      retries = 0
    )
  }

  def absenceClaimRequestFrom(inflection: Inflection, e: Employee, a: Absence): Task[AbsenceClaimRequest] = {
    val sinGenPosition = e.position.fold[Task[String]](ZIO.fail(DomainError.NotEnoughData(s"Position of ${e.employeeId.asString}"))) { p =>
      ZIO.succeed(inflection.dativeAppointment(p.toLowerCase(locale)))
    }

    val sinGenFullName = e.localizedName.fold[Task[String]](ZIO.fail(DomainError.NotEnoughData(s"Localized name of ${e.employeeId.asString}"))) { n =>
      ZIO.succeed(inflection.dativeName(n, e.sex))
    }

    sinGenPosition &&& sinGenFullName map {
      case (sinGenPosition, sinGenFullName) =>
        AbsenceClaimRequest(
          sinGenPosition = sinGenPosition,
          sinGenFullName = sinGenFullName,
          sinGenFromDate = toSinGenDateStr(a.from),
          plurDaysQuantity = s"${a.daysQuantity} ${inflection.pluralize(a.daysQuantity, ("календарный день", "календарных дня", "календарных дней"))}",
          reportDate = toDateStr(a.from.minus(1, ChronoUnit.MONTHS))
        )
    }
  }

  private def toSinGenDateStr(x: LocalDate): String = s"${x.getDayOfMonth} ${x.getMonth.getDisplayName(TextStyle.FULL, locale)} ${x.getYear}"
  private def toDateStr(x: LocalDate): String       = dateFormatter.format(x)
}
