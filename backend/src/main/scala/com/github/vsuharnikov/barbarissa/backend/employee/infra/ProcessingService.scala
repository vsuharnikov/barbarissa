package com.github.vsuharnikov.barbarissa.backend.employee.infra

import java.io.File
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, FormatStyle, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale

import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceAppointmentService.SearchFilter
import com.github.vsuharnikov.barbarissa.backend.employee.domain.AbsenceRepo.GetAfterCursor
import com.github.vsuharnikov.barbarissa.backend.employee.domain._
import com.github.vsuharnikov.barbarissa.backend.meta.ToArgs
import com.github.vsuharnikov.barbarissa.backend.shared.domain.MailService.EmailAddress
import com.github.vsuharnikov.barbarissa.backend.shared.domain.error.{ClaimNotRequired, ForwardError, RepoRecordNotFound, TemplateNotFound}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.{Inflection, MailService, ReportService}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.PadegInflection
import zio.macros.accessible
import zio.{Has, Task, ZIO, ZLayer}

@accessible
object ProcessingService {
  trait Service {
    def refreshQueue: Task[Unit]
    def process: Task[Unit]
  }

  case class Config(templatesDir: File)

  type Dependencies = Has[Config]
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

    new Service {
      override def refreshQueue: Task[Unit] =
        AbsenceQueue.last.flatMap(x => paginatedLoop(GetAfterCursor(x.map(_.absenceId), 0, 10))).provide(env)

      override def process: Task[Unit] =
        AbsenceQueue
          .getUncompleted(10)
          .flatMap { xs =>
            if (xs.isEmpty) ZIO.unit
            else processMany(xs) // *> process // ignore those who failed on a previous step
          }
          .provide(env)

      private def processMany(xs: List[AbsenceQueueItem]): Task[Unit] = Task.foreach(xs)(processOne).unit

      private def processOne(x: AbsenceQueueItem): Task[Unit] =
        ZIO
          .when(!x.done) {
            for {
              absence <- AbsenceRepo.get(x.absenceId).flatMap {
                case None    => ZIO.fail(ForwardError(RepoRecordNotFound))
                case Some(x) => ZIO.succeed(x)
              }
              employee <- EmployeeRepo.get(absence.employeeId).flatMap {
                case Some(x) => ZIO.succeed(x)
                case None    => ZIO.fail(ForwardError(RepoRecordNotFound))
              }
              appointmentCreated <- ZIO
                .when(!x.appointmentCreated) {
                  createAppointment(employee, absence)
                }
                .as(true)
                .catchAll(_ => ZIO.succeed(false))
              claimSent <- ZIO
                .when(!x.claimSent) {
                  sendClaim(employee, absence)
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

      private def sendClaim(employee: Employee, absence: Absence): Task[Unit] = {
        for {
          absenceReason <- AbsenceReasonRepo.get(absence.reasonId).flatMap {
            case Some(x) => ZIO.succeed(x)
            case None    => ZIO.fail(ForwardError(RepoRecordNotFound))
          }
          absenceReasonSuffix <- absenceReason.needClaim match {
            case Some(AbsenceClaimType.WithoutCompensation) => ZIO.succeed("without-compensation")
            case Some(AbsenceClaimType.WithCompensation)    => ZIO.succeed("with-compensation")
            case None                                       => ZIO.fail(ForwardError(ClaimNotRequired))
          }
          templateFile <- {
            val companyId = employee.companyId.map(_.asString).getOrElse("unknown")
            val fileName  = s"$companyId-$absenceReasonSuffix.docx"
            val r         = config.templatesDir.toPath.resolve(fileName).toFile
            if (r.isFile) ZIO.succeed(r)
            else ZIO.fail(ForwardError(TemplateNotFound))
          }
          report <- {
            val data = absenceClaimRequestFrom(PadegInflection, employee, absence) // TODO
            ReportService.generate(templateFile, ToArgs.toArgs(data).toMap)
          }
          _ <- MailService.send(
            to = EmailAddress(employee.email),
            subject = "Заявление на отпуск",
            bodyText = "Подпишите и отправьте в HR",
            attachments = Map(
              "claim.docx" -> report
            )
          )
        } yield ()
      }.provide(env)

      private def createAppointment(employee: Employee, absence: Absence): Task[Unit] = {
        for {
          has <- AbsenceAppointmentService.has(
            SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.absenceId.asString
            ))
          _ <- ZIO.when(!has) {
            val absenceAppointment = AbsenceAppointment(
              subject = s"Ухожу в отпуск ${employee.localizedName.get}",
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
            val xs = unprocessed.view.map { a =>
              toUnprocessed(a, reasonsMap(a.reasonId)) // TODO
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
