package com.github.vsuharnikov.barbarissa.backend.shared.infra

import zio.Has

package object db {
  type MigrationRepo = Has[MigrationRepo.Service]
}
