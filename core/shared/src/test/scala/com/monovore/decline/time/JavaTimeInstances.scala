package com.monovore.decline.time

import java.time._
import java.time.format.DateTimeFormatter

import cats.{Eq, Show}
import com.monovore.decline.Argument

private[time] trait JavaTimeInstances {
  implicit val eqInstant: Eq[Instant] = Eq.fromUniversalEquals
  implicit val showInstant: Show[Instant] = Show.fromToString

  implicit val eqLocalDateTime: Eq[LocalDateTime] = Eq.fromUniversalEquals
  implicit val showLocalDateTime: Show[LocalDateTime] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
  }

  implicit val eqZonedDateTime: Eq[ZonedDateTime] = Eq.fromUniversalEquals
  implicit val showZonedDateTime: Show[ZonedDateTime] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
  }

  implicit val eqOffsetDateTime: Eq[OffsetDateTime] = Eq.fromUniversalEquals
  implicit val showOffsetDateTime: Show[OffsetDateTime] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }

  implicit val eqLocalDate: Eq[LocalDate] = Eq.fromUniversalEquals
  implicit val showLocalDate: Show[LocalDate] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

  implicit val eqLocalTime: Eq[LocalTime] = Eq.fromUniversalEquals
  implicit val showLocalTime: Show[LocalTime] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_LOCAL_TIME)
  }

  implicit val eqMonthDay: Eq[MonthDay] = Eq.fromUniversalEquals
  implicit val showMonthDay: Show[MonthDay] = Show.fromToString
  implicit val argMonthDay: Argument[MonthDay] = monthDayWithFormatter(
    DateTimeFormatter.ofPattern("--MM-dd")
  )

  implicit val eqOffsetTime: Eq[OffsetTime] = Eq.fromUniversalEquals
  implicit val showOffsetTime: Show[OffsetTime] = Show.show { dt =>
    dt.format(DateTimeFormatter.ISO_OFFSET_TIME)
  }

  implicit val eqPeriod: Eq[Period] = Eq.fromUniversalEquals
  implicit val showPeriod: Show[Period] = Show.fromToString

  implicit val eqYear: Eq[Year] = Eq.fromUniversalEquals
  implicit val showYear: Show[Year] = Show.fromToString
  implicit val argYear: Argument[Year] = yearWithFormatter(
    DateTimeFormatter.ofPattern("u")
  )

  implicit val eqYearMonth: Eq[YearMonth] = Eq.fromUniversalEquals
  implicit val showYearMonth: Show[YearMonth] = Show.fromToString
  implicit val argYearMonth: Argument[YearMonth] = yearMonthWithFormatter(
    DateTimeFormatter.ofPattern("u-MM")
  )

  implicit val eqDuration: Eq[Duration] = Eq.fromUniversalEquals
  implicit val showDuration: Show[Duration] = Show.fromToString

  implicit val eqZoneId: Eq[ZoneId] = Eq.fromUniversalEquals
  implicit val showZoneId: Show[ZoneId] = Show.show(_.getId)

  implicit val eqZoneOffset: Eq[ZoneOffset] = Eq.fromUniversalEquals
  implicit val showZoneOffset: Show[ZoneOffset] = Show.fromToString

}
