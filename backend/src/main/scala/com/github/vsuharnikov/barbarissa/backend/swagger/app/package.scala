package com.github.vsuharnikov.barbarissa.backend.swagger

import zio.Has

package object app {
  type SwaggerHttpApiRoutes = Has[SwaggerHttpApiRoutes.Service]
}
