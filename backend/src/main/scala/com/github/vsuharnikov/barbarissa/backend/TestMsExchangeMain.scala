package com.github.vsuharnikov.barbarissa.backend

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl
import microsoft.exchange.webservices.data.core.ExchangeService
import microsoft.exchange.webservices.data.core.enumeration.misc.{ConnectingIdType, ExchangeVersion}
import microsoft.exchange.webservices.data.core.enumeration.property.{LegacyFreeBusyStatus, WellKnownFolderName}
import microsoft.exchange.webservices.data.credential.WebCredentials
import microsoft.exchange.webservices.data.core.PropertySet
import microsoft.exchange.webservices.data.core.service.item.Appointment
import microsoft.exchange.webservices.data.property.complex.{FolderId, MessageBody}
import java.text.SimpleDateFormat

import microsoft.exchange.webservices.data.core.enumeration.search.{FolderTraversal, ItemTraversal}
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder
import microsoft.exchange.webservices.data.core.service.schema.{AppointmentSchema, FolderSchema, ItemSchema}
import microsoft.exchange.webservices.data.misc.ImpersonatedUserId
import microsoft.exchange.webservices.data.search.filter.SearchFilter
import microsoft.exchange.webservices.data.search.{ConversationIndexedItemView, FolderView, ItemView}

import scala.jdk.CollectionConverters._

// https://github.com/OfficeDev/ews-java-api/wiki/Getting-Started-Guide#using-the-library
object TestMsExchangeMain extends App {
  val service     = new ExchangeService(ExchangeVersion.Exchange2010_SP2)
  val credentials = new WebCredentials("", "")
  service.setCredentials(credentials)
  service.autodiscoverUrl("", new RedirectionUrlCallback)

  // https://stackoverflow.com/questions/31854371/ews-create-an-appointment-to-a-specific-calendar
  // service.setImpersonatedUserId(new ImpersonatedUserId(ConnectingIdType.PrincipalName, "")) // <---!!!

  println("Folders:")

  {
    val rootFolder = new FolderId(WellKnownFolderName.Calendar)
    // val searchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Common Views")
    val findResults = service.findFolders(rootFolder, /*searchFilter, */ new FolderView(10))
    findResults.getFolders.asScala.foreach { item =>
      println(s"""===
id: ${item.getId}
display name: ${item.getDisplayName}
folder class: ${item.getFolderClass}
c: ${item.isInstanceOf[CalendarFolder]}
""")

      println("  Subfolders:")
      val view = new FolderView(10)
      view.setTraversal(FolderTraversal.Deep)
      val subFolders = item.findFolders(view)
      subFolders.getFolders.asScala.foreach { item =>
        println(s"""  id: ${item.getId}
  display name: ${item.getDisplayName}
""")
      }

      println("  Subitems:")
      val itemView = new ItemView(10)
      val subItems = item.findItems(itemView)
      subItems.getItems.asScala.foreach { item =>
        println(s"""  id: ${item.getId}
  sub: ${item.getSubject}
  class: ${item.getItemClass}
""")
      }
    }
    println("===\n\n")
  }

//  println("Folder")
//  val folderId = new FolderId(
//    "")
//  service.findFolders(folderId, new FolderView(10)).getFolders.asScala.foreach { folder =>
//    println(s"""  id: ${folder.getId}
//  display name: ${folder.getDisplayName}
//""")
//  }

  println("Items:")

  {
    val findResults = service.findItems(WellKnownFolderName.Calendar, new ItemView(10))

    //MOOOOOOST IMPORTANT: load messages' properties before
    service.loadPropertiesForItems(findResults, PropertySet.FirstClassProperties)

    findResults.getItems.asScala.foreach {
      case item: Appointment =>
        println(s"""appointment id: ${item.getId}
sub: ${item.getSubject}
start: ${item.getStart}
duration: ${item.getDuration}
is all day: ${item.getIsAllDayEvent}
categories: ${item.getCategories.asScala.mkString(", ")}
culture: ${item.getCulture}
folder name: ${item.getParentFolderId.getUniqueId}
icaluid: ${item.getICalUid}
organizer: ${item.getOrganizer.getAddress}""")
        item.getPropertyBag.getProperties.asScala.foreach {
          case (p, v) => println(s"+${p.getName}, ${p.getPrintableName}, ${p.getXmlElement}: $v")
        }
        println()

      case item =>
        println(s"""id: ${item.getId}
sub: ${item.getSubject}
class: ${item.getItemClass}
""")
    }
  }

  println("Conversations:")


  val conversation = {
    service.findConversation(new ConversationIndexedItemView(10), new FolderId("AAMkADc3MGJlZWFhLTk4MWUtNDMzNy04MDA3LTRlYTAzYTMzYjUzMwAuAAAAAAC/efcKGhyMTKH4NGzfnZiOAQB46pI5EOYcSauTuUwWLNLoAAAAAB7mAAA="))
      .asScala.filter { item =>
      println(s"id: ${item.getId}")
      println(s"item classes: ${item.getItemClasses.asScala.mkString(", ")}")
      println(s"global classes: ${item.getGlobalItemClasses.asScala.mkString(", ")}")
      println(s"item ids: ${item.getItemIds.asScala.map(_.getUniqueId)}")
      item.getPropertyBag.getProperties.asScala.foreach {
        case (p, v) => println(s"+${p.getName}, ${p.getPrintableName}, ${p.getXmlElement}: $v")
      }
      println()

      item.getTopic == "AbsenceTest"
    }
  }.head

  if (false) {
    val appointment = new Appointment(service) // ConversationId to parent?
    appointment.setSubject("Going to hell - 6")
    appointment.setBody(MessageBody.getMessageBodyFromText("Some description goes here"))

    val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val startDate = formatter.parse("2020-07-15 00:00:00")
    val endDate   = formatter.parse("2020-07-22 23:59:59")
    appointment.setStart(startDate)
    appointment.setEnd(endDate)
    appointment.setLegacyFreeBusyStatus(LegacyFreeBusyStatus.OOF)
    appointment.setIsAllDayEvent(true)
    appointment.setConferenceType(0) // copied from user's

    // TODO
    // service.bindToItem()

    // TODO
    import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsMode
//    appointment.save(CustomCalendarFolder.Id, SendInvitationsMode.SendToNone) // find a folder

//    appointment.getPropertyBag.setObjectFromPropertyDefinition(ItemSchema.ConversationId, conversation)
    // AAQkADc3MGJlZWFhLTk4MWUtNDMzNy04MDA3LTRlYTAzYTMzYjUzMwAQAI7qY+nvBkJhvf+Dg3RUJEg=


    // appointment.getPropertyBag.setObjectFromPropertyDefinition(AppointmentSchema.Organizer, "absencetest@web3tech.ru")
    appointment.save(WellKnownFolderName.Calendar)
  }

  class RedirectionUrlCallback extends IAutodiscoverRedirectionUrl {
    override def autodiscoverRedirectionUrlValidationCallback(redirectionUrl: String): Boolean = redirectionUrl.toLowerCase.startsWith("https://")
  }
}
