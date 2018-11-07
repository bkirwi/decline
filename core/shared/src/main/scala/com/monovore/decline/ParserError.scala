package com.monovore.decline

import cats.data.NonEmptyList

sealed trait ParserError {
  def withPrefix(prefix: List[String]): ParserError
}
final case class ShowHelp(help: Help) extends ParserError {

  override def withPrefix(prefix: List[String]): ParserError =
    copy(help.withPrefix(prefix))

}
final case class InfoMsg(msg: String, prefix: NonEmptyList[String]) extends ParserError {

  override def withPrefix(newPrefix: List[String]) = {
    NonEmptyList.fromList(newPrefix).fold(this)(np => copy(prefix = np ::: prefix))
  }

}
