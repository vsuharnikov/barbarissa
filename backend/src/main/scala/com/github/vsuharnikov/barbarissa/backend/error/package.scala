package com.github.vsuharnikov.barbarissa.backend

import java.io.{PrintWriter, StringWriter}

package object error {
  def showWithStackTrace(x: Throwable): String = {
    val sw = new StringWriter
    x.printStackTrace(new PrintWriter(sw))
    s"$x\n${sw.toString}"
  }
}
