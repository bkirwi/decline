package com.monovore.decline

import cats.data.NonEmptyList

sealed trait Aborted {
  def errors: List[String]
  def withPrefix(prefix: List[String]): Aborted
}
final case class ShowHelp(help: Help) extends Aborted {

  override def errors: List[String] = help.errors

  override def withPrefix(prefix: List[String]): Aborted =
    copy(help.withPrefix(prefix))

}
final case class ShowMsg(msg: String, prefix: NonEmptyList[String]) extends Aborted {

  override def errors: List[String] = Nil

  override def withPrefix(newPrefix: List[String]) = {
    NonEmptyList.fromList(newPrefix).fold(this)(np => copy(prefix = np ::: prefix))
  }

}
