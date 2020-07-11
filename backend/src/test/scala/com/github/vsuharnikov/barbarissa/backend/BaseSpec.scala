package com.github.vsuharnikov.barbarissa.backend

import com.github.vsuharnikov.cv.backend.test.ZIODiffMatcher
import zio.Cause
import zio.internal.Platform
import zio.test.DefaultRunnableSpec

trait BaseSpec extends DefaultRunnableSpec with ZIODiffMatcher {
  private val debugPlatform = new Platform.Proxy(Platform.default) {
    // It is useful to remove the condition sometimes, e.g. in a case of "Fiber failed"
    override def reportFailure(cause: Cause[Any]): Unit = if (!cause.interrupted) System.err.println(cause.prettyPrint)
  }

  override def runner = super.runner.withPlatform(_ => debugPlatform)
}
