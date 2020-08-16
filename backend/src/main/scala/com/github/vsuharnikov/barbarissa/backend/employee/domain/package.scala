package com.github.vsuharnikov.barbarissa.backend.employee

import zio.Has

package object domain {
  type EmployeeRepo = Has[EmployeeRepo.Service]
}
