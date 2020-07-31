package com.github.vsuharnikov.barbarissa.backend.shared.infra

import zio.Has

package object jira {
  type JiraApi = Has[JiraApi.Service]
}
