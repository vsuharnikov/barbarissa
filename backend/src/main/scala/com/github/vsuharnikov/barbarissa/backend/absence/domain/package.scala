package com.github.vsuharnikov.barbarissa.backend.absence

import zio.Has

package object domain {
  type AbsenceRepo       = Has[AbsenceRepo.Service]
  type AbsenceReasonRepo = Has[AbsenceReasonRepo.Service]
}
