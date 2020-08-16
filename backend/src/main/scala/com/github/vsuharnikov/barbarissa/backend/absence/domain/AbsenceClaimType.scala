package com.github.vsuharnikov.barbarissa.backend.absence.domain

sealed trait AbsenceClaimType extends Product with Serializable
object AbsenceClaimType {
  case object WithCompensation extends AbsenceClaimType
  case object WithoutCompensation extends AbsenceClaimType
}
