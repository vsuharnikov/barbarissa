package com.github.vsuharnikov.barbarissa.backend.queue.domain

import zio.Task
import zio.macros.accessible

@accessible
object AbsenceQueue extends Serializable {
  trait Service extends Serializable {
    def getUncompleted(num: Int): Task[List[AbsenceQueueItem]]
    def add(drafts: List[AbsenceQueueItem]): Task[Unit]
    def update(draft: AbsenceQueueItem): Task[Unit]
    def last(num: Int): Task[List[AbsenceQueueItem]]
  }
}
