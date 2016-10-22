package com.monovore.clique

import cats.arrow.FunctionK
import cats.instances.all._

object Help {

  def render(parser: Command[_]): String = {

    s"""Usage: ${parser.name} ${usage(parser.options).mkString(" ")}
       |
       |${parser.header}
       |""".stripMargin
  }

  type Usage[A] = List[String]

  def usage(opts: Opts[_]): List[String] = opts.value.analyze(new FunctionK[Opt, Usage] {
    override def apply[A](opt: Opt[A]): List[String] = opt match {
      case Opt.Regular(name, metavar, _) => s"--$name" :: metavar :: Nil
      case Opt.Flag(name, _) => s"--$name" :: Nil
    }
  })
}
