package com.monovore.decline.time

import java.time._

import com.monovore.decline.discipline.ArgumentSuite
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._

class JavaTimeSuite extends ArgumentSuite with JavaTimeInstances {

  implicit val arbitraryZoneId: Arbitrary[ZoneId] = Arbitrary {
    Gen.oneOf(ZoneId.getAvailableZoneIds.asScala.map(ZoneId.of).toSeq)
  }

  implicit val arbitraryInstant: Arbitrary[Instant] = Arbitrary {
    Gen.choose(Instant.MIN.getEpochSecond, Instant.MAX.getEpochSecond).map(Instant.ofEpochSecond)
  }

  implicit val arbitraryDuration: Arbitrary[Duration] = Arbitrary {
    for {
      start <- arbitrary[Instant]
      end   <- arbitrary[Instant]
    } yield Duration.between(start, end)
  }

  implicit val arbitraryPeriod: Arbitrary[Period] = Arbitrary {
    for {
      years  <- arbitrary[Int]
      months <- arbitrary[Int]
      days   <- arbitrary[Int]
    } yield Period.of(years, months, days)
  }

  implicit val arbitraryZonedDateTime: Arbitrary[ZonedDateTime] = Arbitrary {
    for {
      instant <- arbitrary[Instant]
      // "Text '-140469387-08-22T15:34:00Z[GMT0]' could not be parsed, unparsed text found at index 26"
      zoneId  <- arbitrary[ZoneId].filter(_ != ZoneId.of("GMT0"))
    } yield instant.atZone(zoneId)
  }

  implicit val arbitraryLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary(arbitrary[ZonedDateTime].map(_.toLocalDateTime))

  implicit val arbitraryLocalDate: Arbitrary[LocalDate] =
    Arbitrary(arbitrary[LocalDateTime].map(_.toLocalDate))

  implicit val arbitraryLocalTime: Arbitrary[LocalTime] =
    Arbitrary(arbitrary[LocalDateTime].map(_.toLocalTime))

  implicit val arbitraryZoneOffset: Arbitrary[ZoneOffset] =
    Arbitrary(Gen.choose(-18 * 60 * 60, 18 * 60 * 60).map(ZoneOffset.ofTotalSeconds))

  implicit val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] = Arbitrary {
    for {
      instant <- arbitrary[Instant]
      offset  <- arbitrary[ZoneOffset]
    } yield instant.atOffset(offset)
  }

  implicit val arbitraryOffsetTime: Arbitrary[OffsetTime] = Arbitrary {
    for {
      time   <- arbitrary[LocalTime]
      offset <- arbitrary[ZoneOffset]
    } yield OffsetTime.of(time, offset)
  }

  implicit val arbitraryMonthDay: Arbitrary[MonthDay] = Arbitrary {
    for {
      leapYear <- arbitrary[Boolean]
      month    <- Gen.choose(1, 12).map(Month.of)
      day      <- Gen.choose(1, month.length(leapYear))
    } yield MonthDay.of(month, day)
  }

  implicit val arbitraryYear: Arbitrary[Year] =
    Arbitrary(arbitrary[LocalDate].map(dt => Year.of(dt.getYear)))

  implicit val arbitraryYearMonth: Arbitrary[YearMonth] =
    Arbitrary(arbitrary[LocalDate].map(dt => YearMonth.of(dt.getYear, dt.getMonthValue)))

  checkArgument[Duration]("java.time.Duration")
  checkArgument[Period]("java.time.Period")

  checkArgument[ZoneId]("java.time.ZoneId")
  checkArgument[Instant]("java.time.Instant")
  checkArgument[ZoneOffset]("java.time.ZoneOffset")

  checkArgument[MonthDay]("java.time.MonthDay")
  checkArgument[YearMonth]("java.time.YearMonth")
  checkArgument[Year]("java.time.Year")

  checkArgument[LocalDate]("java.time.LocalDate")
  checkArgument[LocalTime]("java.time.LocalTime")
  checkArgument[LocalDateTime]("java.time.LocalDateTime")
  checkArgument[ZonedDateTime]("java.time.ZonedDateTime")
  checkArgument[OffsetDateTime]("java.time.OffsetDateTime")
  checkArgument[OffsetTime]("java.time.OffsetTime")

}
