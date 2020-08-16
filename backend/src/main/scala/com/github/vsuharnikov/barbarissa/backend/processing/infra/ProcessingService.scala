package com.github.vsuharnikov.barbarissa.backend.processing.infra

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import com.github.vsuharnikov.barbarissa.backend.absence.domain.AbsenceRepo.GetAfterCursor
import com.github.vsuharnikov.barbarissa.backend.absence.domain._
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{AbsenceAppointment, AbsenceAppointmentService}
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs
import com.github.vsuharnikov.barbarissa.backend.queue.domain.{AbsenceQueue, AbsenceQueueItem}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.MailService.EmailAddress
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{AbsenceId, DomainError, EmployeeId, Inflection, MailService, ReportService}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.PadegInflection
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.{Logger, Logging}
import zio.macros.accessible

@accessible
object ProcessingService {
  trait Service {
    def start: Task[Unit]
    def refreshQueue: Task[Unit]
    def processQueue: Task[Unit]
    def createClaim(aid: AbsenceId): Task[Array[Byte]]
  }

  case class Config(templatesDir: File, refreshQueueInterval: Duration, processQueueInterval: Duration)

  type Dependencies = Has[Config]
    with Clock
    with Logging
    with EmployeeRepo
    with AbsenceRepo
    with AbsenceReasonRepo
    with AbsenceQueue
    with AbsenceAppointmentService
    with ReportService
    with MailService

  private val locale        = Locale.forLanguageTag("ru")
  private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(new Locale("ru"))

  val live = ZLayer.fromFunction[Dependencies, Service] { env =>
    val config = env.get[Config]
    val log    = env.get[Logger[String]]

    new Service {
      override def start: Task[Unit] = {
        val refresh = ZIO.sleep(config.refreshQueueInterval) *> refreshQueue.repeat(Schedule.spaced(config.refreshQueueInterval))
        val process = ZIO.sleep(config.processQueueInterval) *> processQueue.repeat(Schedule.spaced(config.processQueueInterval))
        refresh.forkDaemon *> process.forkDaemon
      }.provide(env).unit

      override def refreshQueue: Task[Unit] = {
        log.info("Refreshing the queue") *>
          AbsenceQueue.last.flatMap(x => paginatedLoop(GetAfterCursor(x.map(_.absenceId), 0, 10)))
      }.provide(env)

      override def processQueue: Task[Unit] = {
        log.info("Processing the queue") *>
          AbsenceQueue
            .getUncompleted(10)
            .flatMap { xs =>
              if (xs.isEmpty) ZIO.unit else processMany(xs)
            }
      }.provide(env)

      override def createClaim(aid: AbsenceId): Task[Array[Byte]] = {
        for {
          absence       <- AbsenceRepo.unsafeGet(aid)
          absenceReason <- AbsenceReasonRepo.unsafeGet(absence.reasonId)
          employee      <- EmployeeRepo.unsafeGet(absence.employeeId)
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
          report <- {
            val data = absenceClaimRequestFrom(PadegInflection, employee, absence) // TODO IoC
            ReportService.generate(templateFile, ToArgs.toArgs(data).toMap)
          }
        } yield report
      }.provide(env)

      private def processMany(xs: List[AbsenceQueueItem]): Task[Unit] = Task.foreach(xs)(processOne(_).ignore).unit

      private def processOne(x: AbsenceQueueItem): Task[Unit] =
        ZIO
          .when(!x.done) {
            for {
              absence       <- AbsenceRepo.unsafeGet(x.absenceId)
              absenceReason <- AbsenceReasonRepo.unsafeGet(absence.reasonId)
              employee      <- EmployeeRepo.unsafeGet(absence.employeeId)
              appointmentCreated <- ZIO
                .when(!x.appointmentCreated) {
                  createAppointment(employee, absence, absenceReason)
                }
                .as(true)
                .catchAll(_ => ZIO.succeed(false))
              claimSent <- ZIO
                .when(!x.claimSent) {
                  sendClaim(employee, absence, absenceReason)
                }
                .as(true)
                .catchAll(_ => ZIO.succeed(false))
              _ <- ZIO.when(appointmentCreated || claimSent) {
                val draft = x.copy(
                  done = appointmentCreated && claimSent,
                  appointmentCreated = appointmentCreated,
                  claimSent = claimSent,
                  retries = x.retries + 1
                )
                AbsenceQueue.update(draft)
              }
            } yield ()
          }
          .provide(env)

      private def sendClaim(employee: Employee, absence: Absence, absenceReason: AbsenceReason): Task[Unit] = {
        for {
          _ <- ZIO.foreach(absenceReason.needClaim) { claimType =>
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
              report <- {
                val data = absenceClaimRequestFrom(PadegInflection, employee, absence) // TODO IoC
                ReportService.generate(templateFile, ToArgs.toArgs(data).toMap)
              }
              _ <- MailService.send(
                to = EmailAddress(employee.email),
                subject = s"Заявление: ${absenceReason.name} с ${absence.from}",
                bodyText = "Подпишите и отправьте в HR",
                attachments = Map(
                  "claim.docx" -> report
                )
              )
            } yield ()
          }
        } yield ()
      }.provide(env)

      private def createAppointment(employee: Employee, absence: Absence, absenceReason: AbsenceReason): Task[Unit] = {
        for {
          localizedName <- employee.localizedName match {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(DomainError.NotEnoughData(s"Employee ${employee.employeeId.asString}, localizedName"))
          }
          has <- AbsenceAppointmentService.has(
            SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.absenceId.asString
            ))
          _ <- ZIO.when(!has) {
            val absenceAppointment = AbsenceAppointment(
              subject = s"$localizedName: ${absenceReason.name}",
              description = "",
              startDate = absence.from,
              endDate = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.absenceId.asString
            )
            AbsenceAppointmentService.add(absenceAppointment)
          }
        } yield ()
      }.provide(env)

      // TODO ZStream
      private def paginatedLoop(cursor: GetAfterCursor): Task[Unit] = {
        for {
          r          <- AbsenceRepo.getFromByCursor(cursor)
          reasonsMap <- AbsenceReasonRepo.all
          _ <- {
            val (unprocessed, nextCursor) = r
            val xs = unprocessed.view.flatMap { a =>
              reasonsMap.get(a.reasonId).map(toUnprocessed(a, _))
            }.toList

            if (xs.isEmpty) ZIO.unit
            else AbsenceQueue.add(xs) *> ZIO.foreach(nextCursor)(paginatedLoop)
          }
        } yield ()
      }.provide(env)
    }
  }

  def toUnprocessed(a: Absence, ar: AbsenceReason): AbsenceQueueItem = AbsenceQueueItem(
    absenceId = a.absenceId,
    done = false,
    claimSent = ar.needClaim.fold(true)(_ => false),
    appointmentCreated = ar.needAppointment.fold(true)(!_),
    retries = 0
  )

  def absenceClaimRequestFrom(inflection: Inflection, e: Employee, a: Absence): AbsenceClaimRequest = AbsenceClaimRequest(
    sinGenPosition = inflection.dativeAppointment(e.position.getOrElse("???").toLowerCase(locale)), // TODO
    sinGenFullName = inflection.dativeName(e.localizedName.getOrElse("???"), e.sex),
    sinGenFromDate = toSinGenDateStr(a.from),
    daysQuantity = a.daysQuantity,
    reportDate = toDateStr(a.from.minus(1, ChronoUnit.MONTHS))
  )

  private def toSinGenDateStr(x: LocalDate): String = s"${x.getDayOfMonth} ${x.getMonth.getDisplayName(TextStyle.FULL, locale)} ${x.getYear}"
  private def toDateStr(x: LocalDate): String       = dateFormatter.format(x)
}
