package com.github.vsuharnikov.barbarissa.backend.meta

import java.time.LocalDate

import magnolia._

import scala.language.experimental.macros

trait ToArgs[T] {
  def toArgs(x: T): List[(String, String)]
}

object ToArgs {
  type Typeclass[T] = ToArgs[T]

  def apply[T](implicit ev: ToArgs[T]): ToArgs[T]     = ev
  def toArgs[T: ToArgs](x: T): List[(String, String)] = apply[T].toArgs(x)

  def empty[T]: ToArgs[T] = (_: T) => List.empty

  implicit val string: ToArgs[String]       = empty
  implicit val localDate: ToArgs[LocalDate] = empty
  implicit val int: ToArgs[Int]             = empty

  def combine[T](ctx: CaseClass[ToArgs, T]): ToArgs[T] = { x =>
    ctx.parameters.map { param =>
      param.label -> param.dereference(x).toString
    }.toList
  }

  def dispatch[T](ctx: SealedTrait[ToArgs, T]): ToArgs[T] = { x =>
    ctx.dispatch(x) { sub =>
      sub.typeclass.toArgs(sub.cast(x))
    }
  }

  implicit def gen[T]: ToArgs[T] = macro Magnolia.gen[T]
}
