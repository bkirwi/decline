package com.monovore.decline

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

object PlatformApp {

  @js.native
  @JSGlobalScope
  private[this] object Process extends js.Object {
    def process: js.UndefOr[Process] = js.native
  }

  @js.native
  private[this] trait Process extends js.Object {
    def argv: js.Array[String] = js.native

    def env: js.Object = js.native
  }

  /**
   * Returns `Some(argument list)` when compiled with Scala.js and running under Node.js, and `None`
   * otherwise.
   */
  def ambientArgs: Option[Seq[String]] = Process.process.toOption.map { _.argv.drop(2).toSeq }

  /**
   * Returns `Some(environment map)` when compiled with Scala.js and running under Node.js, and
   * `None` otherwise.
   */
  def ambientEnvs: Option[Map[String, String]] = Process.process.toOption
    .map { process =>
      js.Object
        .entries(process.env)
        .map(js.Tuple2.unapply)
        .flatten
        .toMap
        .mapValues(_.toString)
    }
}
