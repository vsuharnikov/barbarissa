package com.github.vsuharnikov.barbarissa.backend.employee.infra

import java.time.{LocalDate, ZoneId}
import java.util.{Date, TimeZone}

import com.github.vsuharnikov.barbarissa.backend.employee.domain.{AbsenceAppointment, AbsenceAppointmentService}
import com.github.vsuharnikov.barbarissa.backend.shared.infra.MsExchangeService
import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl
import microsoft.exchange.webservices.data.core.enumeration.misc.{ExchangeVersion, TraceFlags}
import microsoft.exchange.webservices.data.core.enumeration.property._
import microsoft.exchange.webservices.data.core.enumeration.search.{FolderTraversal, LogicalOperator}
import microsoft.exchange.webservices.data.core.service.folder.{CalendarFolder, Folder}
import microsoft.exchange.webservices.data.core.service.item.{Appointment, Item}
import microsoft.exchange.webservices.data.core.service.schema.{AppointmentSchema, FolderSchema}
import microsoft.exchange.webservices.data.core.{ExchangeService, PropertySet}
import microsoft.exchange.webservices.data.credential.WebCredentials
import microsoft.exchange.webservices.data.property.complex.MessageBody
import microsoft.exchange.webservices.data.property.complex.time.OlsonTimeZoneDefinition
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition
import microsoft.exchange.webservices.data.search.filter.SearchFilter
import microsoft.exchange.webservices.data.search.filter.SearchFilter.SearchFilterCollection
import microsoft.exchange.webservices.data.search.{FindItemsResults, FolderView, ItemView}
import zio.{Has, Task, ZIO, ZLayer}

import scala.jdk.CollectionConverters._

object MsExchangeAbsenceAppointmentService {
  case class Config(searchPageSize: Int, zoneId: ZoneId)

  private val jiraTaskKeyPropDef = new ExtendedPropertyDefinition(
    DefaultExtendedPropertySet.PublicStrings,
    "JiraTaskKey",
    MapiPropertyType.String
  )

  val live = ZLayer.fromServicesM[Config, MsExchangeService.Service, Any, Throwable, AbsenceAppointmentService.Service] { (config, service) =>
    for {
      service        <- service.get
      calendarFolder <- findCalendarFolder(service)
    } yield
      new AbsenceAppointmentService.Service {
        private val msExchangeTimeZone = new OlsonTimeZoneDefinition(TimeZone.getTimeZone(config.zoneId))
        private val hasPropertySet     = new PropertySet(jiraTaskKeyPropDef)
        private val getPropertySet     = new PropertySet(BasePropertySet.FirstClassProperties, jiraTaskKeyPropDef)

        override def has(filter: AbsenceAppointmentService.SearchFilter): Task[Boolean] =
          has(toView(filter, config.searchPageSize), filter.serviceMark)

        override def get(filter: AbsenceAppointmentService.SearchFilter): Task[Option[AbsenceAppointment]] =
          get(toView(filter, config.searchPageSize), filter.serviceMark).map(_.map(toAbsenceAppointment))

        override def add(appointment: AbsenceAppointment): Task[Unit] =
          Task(toAppointment(appointment).save(calendarFolder.getId))

        private def has(view: ItemView, jiraTaskValue: String): Task[Boolean] =
          for {
            items  <- Task.effect(calendarFolder.findItems(view))
            hasNow <- Task.effect(hasIn(items, jiraTaskValue))
            has <- if (hasNow || !items.isMoreAvailable) Task(hasNow)
            else {
              view.setOffset(items.getNextPageOffset)
              has(view, jiraTaskValue)
            }
          } yield has

        private def hasIn(items: FindItemsResults[Item], jiraTaskValue: String): Boolean = {
          service.loadPropertiesForItems(items, hasPropertySet)
          items.asScala.exists { appointment =>
            appointment.getExtendedProperties.asScala.exists { property =>
              property.getPropertyDefinition == jiraTaskKeyPropDef && property.getValue == jiraTaskValue
            }
          }
        }

        private def get(view: ItemView, jiraTaskValue: String): Task[Option[Appointment]] =
          for {
            items          <- Task.effect(calendarFolder.findItems(view))
            appointmentNow <- Task.effect(getIn(items, jiraTaskValue))
            appointment <- if (appointmentNow.isDefined || !items.isMoreAvailable) Task(appointmentNow)
            else {
              view.setOffset(items.getNextPageOffset)
              get(view, jiraTaskValue)
            }
          } yield appointment

        private def getIn(items: FindItemsResults[Item], jiraTaskValue: String): Option[Appointment] = {
          service.loadPropertiesForItems(items, getPropertySet)
          items.asScala
            .find { item =>
              item.getExtendedProperties.asScala.exists { property =>
                property.getPropertyDefinition == jiraTaskKeyPropDef && property.getValue == jiraTaskValue
              }
            }
            .collect { case appointment: Appointment => appointment }
        }

        private def toAppointment(appointment: AbsenceAppointment): Appointment = {
          val r = new Appointment(service)
          r.setSubject(appointment.subject)
          r.setSensitivity(Sensitivity.Normal)
          r.setBody(MessageBody.getMessageBodyFromText(appointment.description))
          r.setImportance(Importance.Normal)
          r.setIsReminderSet(false)
          r.setReminderDueBy(toDate(appointment.startDate))
          r.setIsReminderSet(false)
          r.setReminderMinutesBeforeStart(15)
          r.setCulture("ru-RU")
          r.setStart(toDate(appointment.startDate))
          r.setEnd(toDate(appointment.endDate))
          r.setIsAllDayEvent(true)
          r.setLegacyFreeBusyStatus(LegacyFreeBusyStatus.OOF)
          r.setIsResponseRequested(false)
          r.setStartTimeZone(msExchangeTimeZone)
          r.setEndTimeZone(msExchangeTimeZone)
          r.setAllowNewTimeProposal(false)
          r.setExtendedProperty(jiraTaskKeyPropDef, appointment.serviceMark)
          r
        }

        private def toAbsenceAppointment(x: Appointment): AbsenceAppointment = AbsenceAppointment(
          subject = x.getSubject,
          description = x.getBody.toString,
          startDate = toLocalDate(x.getStart),
          endDate = toLocalDate(x.getEnd),
          serviceMark = x.getExtendedProperties.asScala
            .collectFirst {
              case x if x.getPropertyDefinition == jiraTaskKeyPropDef => x.getValue.toString
            }
            .getOrElse("") // Impossible
        )

        private def toView(filter: AbsenceAppointmentService.SearchFilter, searchPageSize: Int): ItemView =
          MsExchangeAbsenceAppointmentService.toView(filter, searchPageSize, config.zoneId)

        private def toDate(x: LocalDate): Date = MsExchangeAbsenceAppointmentService.toDate(x, config.zoneId)
      }
  }

  private def toView(filter: AbsenceAppointmentService.SearchFilter, searchPageSize: Int, zoneId: ZoneId): ItemView = {
    val itemView               = new ItemView(searchPageSize, 0)
    val searchFilterCollection = new SearchFilterCollection(LogicalOperator.And)
    searchFilterCollection.add(new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start, toDate(filter.start, zoneId)))
    searchFilterCollection.add(new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.End, toDate(filter.end, zoneId)))
    itemView
  }

  private def toDate(x: LocalDate, zoneId: ZoneId): Date = Date.from(x.atStartOfDay(zoneId).toInstant)

  private def toLocalDate(x: Date): LocalDate = LocalDate.from(x.toInstant)

  private def findCalendarFolder(service: ExchangeService): Task[CalendarFolder] = Task {
    val rootFolder = Folder.bind(service, WellKnownFolderName.Root)

    val filter = new SearchFilterCollection(LogicalOperator.And)
    filter.add(new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Calendar"))
    filter.add(new SearchFilter.IsEqualTo(FolderSchema.FolderClass, "IPF.Appointment"))

    val view = new FolderView(2)
    view.setTraversal(FolderTraversal.Deep)

    val folders       = rootFolder.findFolders(filter, view).getFolders
    val foldersNumber = folders.size()
    if (foldersNumber == 0) throw new RuntimeException("Can't find a Calendar folder. Try to switch a user.")
    else if (foldersNumber > 1) throw new RuntimeException("Found multiple Calendar folders. Ask a programmer.")
    else
      folders.get(0) match {
        case folder: CalendarFolder => folder
        case folder                 => throw new RuntimeException(s"Found a non-calendar folder: ${folder.getClass.getName}. Ask a programmer.")
      }
  }
}
