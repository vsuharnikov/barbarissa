package com.github.vsuharnikov.barbarissa.backend.test

import com.softwaremill.diffx.{Diff, DiffResultDifferent}
import zio.test.Assertion
import zio.test.Assertion.Render.param

// TODO https://github.com/softwaremill/diffx/pull/63
trait ZIODiffMatcher {

  final def matchTo[A: Diff](expected: A): Assertion[A] =
    Assertion.assertion("matchTo")(param(expected)) { actual =>
      Diff[A].apply(actual, expected) match {
        case c: DiffResultDifferent => throw new RuntimeException(c.show)
        case _                      => true
      }
    }

}
