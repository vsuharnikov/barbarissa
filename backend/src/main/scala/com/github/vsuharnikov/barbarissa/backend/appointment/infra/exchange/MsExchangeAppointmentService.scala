package com.github.vsuharnikov.barbarissa.backend.appointment.infra.exchange

import java.time.{LocalDate, ZoneId}
import java.util.{Date, TimeZone}

import com.github.vsuharnikov.barbarissa.backend.appointment.domain.{Appointment, AppointmentService}
import com.github.vsuharnikov.barbarissa.backend.shared.domain.DomainError
import com.github.vsuharnikov.barbarissa.backend.shared.infra.exchange.MsExchangeService
import microsoft.exchange.webservices.data.core.enumeration.property._
import microsoft.exchange.webservices.data.core.enumeration.search.{FolderTraversal, LogicalOperator}
import microsoft.exchange.webservices.data.core.service.folder.{CalendarFolder, Folder}
import microsoft.exchange.webservices.data.core.service.item.{Item, Appointment => MsAppointment}
import microsoft.exchange.webservices.data.core.service.schema.{AppointmentSchema, FolderSchema}
import microsoft.exchange.webservices.data.core.{ExchangeService, PropertySet}
import microsoft.exchange.webservices.data.property.complex.time.OlsonTimeZoneDefinition
import microsoft.exchange.webservices.data.property.complex.{FolderId, MessageBody}
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition
import microsoft.exchange.webservices.data.search.filter.SearchFilter
import microsoft.exchange.webservices.data.search.filter.SearchFilter.SearchFilterCollection
import microsoft.exchange.webservices.data.search.{FindItemsResults, FolderView, ItemView}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock

import scala.jdk.CollectionConverters._

object MsExchangeAppointmentService {
  case class Config(
      targetCalendarFolder: TargetCalendarFolderConfig,
      searchPageSize: Int,
      zoneId: ZoneId,
      retryPolicy: MsExchangeService.RetryPolicyConfig
  )
  sealed trait TargetCalendarFolderConfig extends Product with Serializable
  object TargetCalendarFolderConfig {
    case object AutoDiscover           extends TargetCalendarFolderConfig
    case class Fixed(uniqueId: String) extends TargetCalendarFolderConfig
  }

  private val jiraTaskKeyPropDef = new ExtendedPropertyDefinition(
    DefaultExtendedPropertySet.PublicStrings,
    "JiraTaskKey",
    MapiPropertyType.String
  )

  private val hasPropertySet = new PropertySet(jiraTaskKeyPropDef)
  private val getPropertySet = new PropertySet(BasePropertySet.FirstClassProperties, jiraTaskKeyPropDef)

  type Dependencies = Has[Config] with Clock with Blocking with Has[ExchangeService]

  val live: ZLayer[Dependencies, Throwable, Has[AppointmentService.Service]] = ZIO
    .accessM[Dependencies] { env =>
      val config      = env.get[Config]
      val es          = env.get[ExchangeService]
      val blocking    = env.get[Blocking.Service]
      val retryPolicy = MsExchangeService.retryPolicy(config.retryPolicy).provide(env)

      for {
        calendarFolder <- blocking
          .effectBlocking {
            config.targetCalendarFolder match {
              case TargetCalendarFolderConfig.AutoDiscover    => findCalendarFolder(es)
              case TargetCalendarFolderConfig.Fixed(uniqueId) => CalendarFolder.bind(es, new FolderId(uniqueId))
            }
          }
          .retry(retryPolicy)
      } yield
        new AppointmentService.Service {
          private val msExchangeTimeZone = new OlsonTimeZoneDefinition(TimeZone.getTimeZone(config.zoneId))

          override def has(filter: AppointmentService.SearchFilter): Task[Boolean] =
            has(toView(config.searchPageSize), toSearchFilter(filter), filter.serviceMark).retry(retryPolicy).provide(env)

          override def get(filter: AppointmentService.SearchFilter): Task[Option[Appointment]] =
            internalGet(toView(config.searchPageSize), toSearchFilter(filter), filter.serviceMark)
              .map(_.map(toAppointment))
              .retry(retryPolicy)
              .provide(env)

          override def add(appointment: Appointment): Task[Unit] =
            blocking
              .effectBlocking(toMsAppointment(appointment).save(calendarFolder.getId))
              .retry(retryPolicy)
              .provide(env)

          private def has(view: ItemView, searchFilter: SearchFilter, jiraTaskValue: String): Task[Boolean] =
            internalHas(view, searchFilter, jiraTaskValue)

          private def internalHas(view: ItemView, searchFilter: SearchFilter, jiraTaskValue: String): Task[Boolean] =
            for {
              items  <- findItemsF(view, searchFilter)
              hasNow <- hasInF(items, jiraTaskValue)
              has <- if (hasNow || !items.isMoreAvailable) Task(hasNow)
              else {
                view.setOffset(items.getNextPageOffset)
                internalHas(view, searchFilter, jiraTaskValue)
              }
            } yield has

          private def hasInF(items: FindItemsResults[Item], jiraTaskValue: String): Task[Boolean] =
            blocking.effectBlocking(hasIn(items, jiraTaskValue))

          private def hasIn(items: FindItemsResults[Item], jiraTaskValue: String): Boolean =
            if (items.getItems.isEmpty) false
            else {
              es.loadPropertiesForItems(items, hasPropertySet)
              items.asScala.exists { appointment =>
                appointment.getExtendedProperties.asScala.exists { property =>
                  property.getPropertyDefinition == jiraTaskKeyPropDef && property.getValue == jiraTaskValue
                }
              }
            }

          private def internalGet(view: ItemView, searchFilter: SearchFilter, jiraTaskValue: String): Task[Option[MsAppointment]] =
            for {
              items          <- findItemsF(view, searchFilter)
              appointmentNow <- getInF(items, jiraTaskValue)
              appointment <- if (appointmentNow.isDefined || !items.isMoreAvailable) Task.succeed(appointmentNow)
              else {
                view.setOffset(items.getNextPageOffset)
                internalGet(view, searchFilter, jiraTaskValue)
              }
            } yield appointment

          private def findItemsF(view: ItemView, searchFilter: SearchFilter): Task[FindItemsResults[Item]] =
            blocking.effectBlocking(calendarFolder.findItems(searchFilter, view))

          private def getInF(items: FindItemsResults[Item], jiraTaskValue: String): Task[Option[MsAppointment]] =
            blocking.effectBlocking(getIn(items, jiraTaskValue))

          private def getIn(items: FindItemsResults[Item], jiraTaskValue: String): Option[MsAppointment] =
            if (items.getItems.isEmpty) None
            else {
              es.loadPropertiesForItems(items, getPropertySet)
              items.asScala
                .find { item =>
                  item.getExtendedProperties.asScala.exists { property =>
                    property.getPropertyDefinition == jiraTaskKeyPropDef && property.getValue == jiraTaskValue
                  }
                }
                .collect { case appointment: MsAppointment => appointment }
            }

          private def toMsAppointment(appointment: Appointment): MsAppointment = {
            val r = new MsAppointment(es)
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

          private def toAppointment(x: MsAppointment): Appointment = Appointment(
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

          private def toSearchFilter(filter: AppointmentService.SearchFilter): SearchFilter =
            MsExchangeAppointmentService.toSearchFilter(filter, config.zoneId)

          private def toDate(x: LocalDate): Date = MsExchangeAppointmentService.toDate(x, config.zoneId)

          private def toLocalDate(x: Date): LocalDate = MsExchangeAppointmentService.toLocalDate(x, config.zoneId)
        }
    }
    .toLayer

  private def toView(searchPageSize: Int): ItemView = new ItemView(searchPageSize, 0)

  private def toSearchFilter(filter: AppointmentService.SearchFilter, zoneId: ZoneId): SearchFilter = {
    val r = new SearchFilterCollection(LogicalOperator.And)
    r.add(new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start, toDate(filter.start, zoneId)))
    r.add(new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.End, toDate(filter.end, zoneId)))
    r
  }

  private def toDate(x: LocalDate, zoneId: ZoneId): Date = Date.from(x.atStartOfDay(zoneId).toInstant)

  private def toLocalDate(x: Date, zoneId: ZoneId): LocalDate = x.toInstant.atZone(zoneId).toLocalDate

  private def findCalendarFolder(service: ExchangeService): CalendarFolder = {
    val rootFolder = Folder.bind(service, WellKnownFolderName.Root)

    val filter = new SearchFilterCollection(LogicalOperator.And)
    filter.add(new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Calendar"))
    filter.add(new SearchFilter.IsEqualTo(FolderSchema.FolderClass, "IPF.Appointment"))

    val view = new FolderView(2)
    view.setTraversal(FolderTraversal.Deep)

    val folders       = rootFolder.findFolders(filter, view).getFolders
    val foldersNumber = folders.size()
    if (foldersNumber == 0) throw DomainError.ConfigurationError("Can't find a Calendar folder. Try to switch a user")
    else if (foldersNumber > 1) throw DomainError.UnhandledError("Found multiple Calendar folders")
    else
      folders.get(0) match {
        case folder: CalendarFolder => folder
        case folder                 => throw DomainError.ConfigurationError(s"Found a non-calendar folder: ${folder.getClass.getSimpleName}")
      }

    // log folder id
  }
}
