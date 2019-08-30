package com.monovore.decline.enumeratum

import cats.{Eq, Show}

import enumeratum._
import enumeratum.EnumEntry._
import enumeratum.values._

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalacheck.{Gen, Arbitrary}

import com.monovore.decline.discipline.ArgumentSuite

sealed trait Greeting extends EnumEntry with Uppercase
object Greeting extends Enum[Greeting] {
  case object Hello extends Greeting
  case object GoodBye extends Greeting with Lowercase

  val values = findValues

  implicit val arbitraryGreeting: Arbitrary[Greeting] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqGreeting: Eq[Greeting] = Eq.fromUniversalEquals
  implicit val showGreeting: Show[Greeting] = Show.show(_.entryName)
}

sealed abstract class WeekDay(val value: Int) extends IntEnumEntry
object WeekDay extends IntEnum[WeekDay] {
  case object Monday extends WeekDay(0)
  case object Tuesday extends WeekDay(1)
  case object Wednesday extends WeekDay(2)
  case object Thursday extends WeekDay(3)
  case object Friday extends WeekDay(4)
  case object Saturday extends WeekDay(5)
  case object Sunday extends WeekDay(6)

  val values = findValues

  implicit val arbitraryWeekDay: Arbitrary[WeekDay] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqWeekDay: Eq[WeekDay] = Eq.fromUniversalEquals
  implicit val showWeekDay: Show[WeekDay] = Show.show(_.value.toString())
}

sealed abstract class Card(val value: Long) extends LongEnumEntry
object Card extends LongEnum[Card] {
  case object Ace extends Card(1L)
  case object King extends Card(10L)
  case object Queen extends Card(9L)

  val values = findValues

  implicit val arbitraryCard: Arbitrary[Card] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqCard: Eq[Card] = Eq.fromUniversalEquals
  implicit val showCard: Show[Card] = Show.show(_.value.toString())
}

sealed abstract class ShortCard(val value: Short) extends ShortEnumEntry
object ShortCard extends ShortEnum[ShortCard] {
  case object Ace extends ShortCard(1)
  case object King extends ShortCard(10)
  case object Queen extends ShortCard(9)

  val values = findValues

  implicit val arbitraryShortCard: Arbitrary[ShortCard] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqShortCard: Eq[ShortCard] = Eq.fromUniversalEquals
  implicit val showShortCard: Show[ShortCard] = Show.show(_.value.toString())
}

sealed abstract class CharCard(val value: Char) extends CharEnumEntry
object CharCard extends CharEnum[CharCard] {
  case object Ace extends CharCard('a')
  case object King extends CharCard('k')
  case object Queen extends CharCard('q')

  val values = findValues

  implicit val arbitraryCharCard: Arbitrary[CharCard] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqCharCard: Eq[CharCard] = Eq.fromUniversalEquals
  implicit val showCharCard: Show[CharCard] = Show.show(_.value.toString())
}

sealed abstract class ByteCard(val value: Byte) extends ByteEnumEntry
object ByteCard extends ByteEnum[ByteCard] {
  case object Ace extends ByteCard(0)
  case object King extends ByteCard(1)
  case object Queen extends ByteCard(2)

  val values = findValues

  implicit val arbitraryByteCard: Arbitrary[ByteCard] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqByteCard: Eq[ByteCard] = Eq.fromUniversalEquals
  implicit val showByteCard: Show[ByteCard] = Show.show(_.value.toString())
}

sealed abstract class Option(val value: String) extends StringEnumEntry
object Option extends StringEnum[Option] {
  case object A extends Option("option-a")
  case object B extends Option("option-b")
  case object C extends Option("option-c")

  val values = findValues

  implicit val arbitraryOption: Arbitrary[Option] =
    Arbitrary(Gen.oneOf(values))

  implicit val eqOption: Eq[Option] = Eq.fromUniversalEquals
  implicit val showOption: Show[Option] = Show.show(_.value.toString())
}

class EnumeratumArgumentSpec extends ArgumentSuite {

  checkArgument[Greeting]("Greeting")
  checkArgument[WeekDay]("WeekDay")
  checkArgument[Card]("Card")
  checkArgument[ShortCard]("ShortCard")
  checkArgument[CharCard]("CharCard")
  checkArgument[ByteCard]("ByteCard")
  checkArgument[Option]("Option")

}