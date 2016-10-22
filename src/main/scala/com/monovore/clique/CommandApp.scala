package com.monovore.clique

class CommandApp(program: String, header: String, options: Opts[Unit]) {

  def main(args: Array[String]): Unit = {
    Parse.run(args.toList, options)
      .valueOr { errors =>
        errors.foreach(System.err.println)
      }
  }
}
