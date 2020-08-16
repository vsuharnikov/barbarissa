package com.github.vsuharnikov.barbarissa.backend.appointment.domain

import java.time.LocalDate

import zio.Task
import zio.macros.accessible

@accessible
object AppointmentService extends Serializable {
  trait Service extends Serializable {
    def has(filter: SearchFilter): Task[Boolean]
    def get(filter: SearchFilter): Task[Option[Appointment]]
    def add(appointment: Appointment): Task[Unit]
  }

  case class SearchFilter(start: LocalDate, end: LocalDate, serviceMark: String)
}
