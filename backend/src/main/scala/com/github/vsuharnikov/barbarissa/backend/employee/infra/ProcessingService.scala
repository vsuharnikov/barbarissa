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
    val config                    = env.get[Config]
    val employeeRepo              = env.get[EmployeeRepo.Service]
    val absenceRepo               = env.get[AbsenceRepo.Service]
    val reasonRepo                = env.get[AbsenceReasonRepo.Service]
    val queueRepo                 = env.get[AbsenceQueue.Service]
    val absenceAppointmentService = env.get[AbsenceAppointmentService.Service]
    val reportService             = env.get[ReportService.Service]
    val mailService               = env.get[MailService.Service]

    new Service {
      override def refreshQueue: Task[Unit] =
        queueRepo.last.flatMap(x => paginatedLoop(GetAfterCursor(x.map(_.absenceId), 0, 10)))

      override def process: Task[Unit] =
        queueRepo.getUncompleted(10).flatMap { xs =>
          if (xs.isEmpty) Task.unit
          else processMany(xs) // *> process // ignore those who failed on a previous step
        }

      private def processMany(xs: List[AbsenceQueueItem]): Task[Unit] = Task.foreach(xs)(processOne).unit

      private def processOne(x: AbsenceQueueItem): Task[Unit] =
        ZIO
          .when(!x.done) {
            for {
              absence  <- AbsenceRepo.get(x.absenceId)
              employee <- EmployeeRepo.get(absence.employeeId)
              employee <- employee match {
                case Some(x) => ZIO.succeed(x)
                case None    => ZIO.fail(ForwardError(RepoRecordNotFound))
              }
              appointmentCreated <- Task
                .when(!x.appointmentCreated) {
                  sendAppointment(employee, absence)
                }
                .as(true)
                .catchAll(_ => Task.succeed(false))
              claimSent <- Task
                .when(!x.claimSent) {
                  sendClaim(employee, absence)
                }
                .as(true)
                .catchAll(_ => Task.succeed(false))
              _ <- Task.when(appointmentCreated || claimSent) {
                val draft = x.copy(
                  done = appointmentCreated && claimSent,
                  appointmentCreated = appointmentCreated,
                  claimSent = claimSent,
                  retries = x.retries + 1
                )
                queueRepo.update(draft)
              }
            } yield ()
          }
          .provide(env)

      private def sendClaim(employee: Employee, absence: Absence): Task[Unit] = {
        val r = for {
          absenceReason <- AbsenceReasonRepo.get(absence.reason.id).mapError(ForwardError)
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
          _ <- mailService.send(
            to = EmailAddress(employee.email),
            subject = "Заявление на отпуск",
            bodyText = "Подпишите и отправьте в HR",
            attachments = Map(
              "claim.docx" -> report
            )
          )
        } yield ()

        r.provide(env)
      }

      private def sendAppointment(employee: Employee, absence: Absence): Task[Unit] = {
        val r = for {
          has <- {
            val searchFilter = SearchFilter(
              start = absence.from,
              end = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.id.asString
            )
            AbsenceAppointmentService.has(searchFilter)
          }
          _ <- ZIO.when(!has) {
            val absenceAppointment = AbsenceAppointment(
              subject = s"Ухожу в отпуск ${employee.localizedName.get}",
              description = "",
              startDate = absence.from,
              endDate = absence.from.plusDays(absence.daysQuantity),
              serviceMark = absence.id.asString
            )
            AbsenceAppointmentService.add(absenceAppointment)
          }
        } yield ()
        r.provide(env)
      }

      // TODO ZStream
      private def paginatedLoop(cursor: GetAfterCursor): Task[Unit] =
        for {
          r          <- absenceRepo.getFromByCursor(cursor)
          reasonsMap <- reasonRepo.all.mapError(ForwardError)
          _ <- {
            val (unprocessed, nextCursor) = r
            val xs = unprocessed.view.map { a =>
              toUnprocessed(a, reasonsMap(a.reason.id)) // TODO
            }.toList

            if (xs.isEmpty) Task.unit
            else {
              val draftLastKnown = unprocessed.last.id
              queueRepo.add(xs) *> Task.foreach(nextCursor)(paginatedLoop)
            }
          }
        } yield ()
    }
  }

  def toUnprocessed(a: Absence, ar: AbsenceReason): AbsenceQueueItem = AbsenceQueueItem(
    absenceId = a.id,
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
