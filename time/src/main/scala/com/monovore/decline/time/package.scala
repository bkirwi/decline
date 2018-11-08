package com.monovore.decline

import java.time._
import java.time.format.DateTimeFormatter

package object time {

  implicit lazy val defaultZoneId: Argument[ZoneId] =
    new JavaTimeArgument[ZoneId]("ZONE_ID") {
      override protected def parseUnsafe(input: String): ZoneId = ZoneId.of(input)
    }

  implicit lazy val defaultZoneOffset: Argument[ZoneOffset] =
    new JavaTimeArgument[ZoneOffset]("ZONE_OFFSET") {
      override protected def parseUnsafe(input: String): ZoneOffset = ZoneOffset.of(input)
    }

  implicit lazy val defaultDuration: Argument[Duration] =
    new JavaTimeArgument[Duration]("DURATION") {
      override protected def parseUnsafe(input: String): Duration = Duration.parse(input)
    }

  implicit lazy val defaultPeriod: Argument[Period] =
    new JavaTimeArgument[Period]("PERIOD") {
      override protected def parseUnsafe(input: String): Period = Period.parse(input)
    }

  implicit lazy val defaultInstant: Argument[Instant] = new JavaTimeArgument[Instant]("INSTANT") {
    override protected def parseUnsafe(input: String): Instant =
      Instant.parse(input)
  }

  final def monthDayWithFormatter(formatter: DateTimeFormatter): Argument[MonthDay] =
    new JavaTimeArgument[MonthDay]("MONTH-DAY") {
      override protected def parseUnsafe(input: String): MonthDay =
        MonthDay.parse(input, formatter)
    }

  final def yearWithFormatter(formatter: DateTimeFormatter): Argument[Year] =
    new JavaTimeArgument[Year]("YEAR") {
      override protected def parseUnsafe(input: String): Year =
        Year.parse(input, formatter)
    }

  final def yearMonthWithFormatter(formatter: DateTimeFormatter): Argument[YearMonth] =
    new JavaTimeArgument[YearMonth]("YEAR-MONTH") {
      override protected def parseUnsafe(input: String): YearMonth =
        YearMonth.parse(input, formatter)
    }

  implicit lazy val defaultLocalDate: Argument[LocalDate] =
    localDateWithFormatter(DateTimeFormatter.ISO_LOCAL_DATE)

  final def localDateWithFormatter(formatter: DateTimeFormatter): Argument[LocalDate] =
    new JavaTimeArgument[LocalDate]("LOCAL_DATE") {
      override protected def parseUnsafe(input: String): LocalDate =
        LocalDate.parse(input, formatter)
    }

  implicit lazy val defaultLocalTime: Argument[LocalTime] =
    localTimeWithFormatter(DateTimeFormatter.ISO_LOCAL_TIME)

  final def localTimeWithFormatter(formatter: DateTimeFormatter): Argument[LocalTime] =
    new JavaTimeArgument[LocalTime]("LOCAL_TIME") {
      override protected def parseUnsafe(input: String): LocalTime =
        LocalTime.parse(input, formatter)
    }

  implicit lazy val defaultLocalDateTime: Argument[LocalDateTime] =
    localDateTimeWithFormatter(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  final def localDateTimeWithFormatter(formatter: DateTimeFormatter): Argument[LocalDateTime] =
    new JavaTimeArgument[LocalDateTime]("LOCAL_DATE_TIME") {
      override protected def parseUnsafe(input: String): LocalDateTime =
        LocalDateTime.parse(input, formatter)
    }

  implicit lazy val defaultOffsetTime: Argument[OffsetTime] =
    offsetTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_TIME)

  final def offsetTimeWithFormatter(formatter: DateTimeFormatter): Argument[OffsetTime] =
    new JavaTimeArgument[OffsetTime]("OFFSET_TIME") {
      override protected def parseUnsafe(input: String): OffsetTime =
        OffsetTime.parse(input, formatter)
    }

  implicit lazy val defaultOffsetDateTime: Argument[OffsetDateTime] =
    offsetDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  final def offsetDateTimeWithFormatter(formatter: DateTimeFormatter): Argument[OffsetDateTime] =
    new JavaTimeArgument[OffsetDateTime]("OFFSET_DATE_TIME") {
      override protected def parseUnsafe(input: String): OffsetDateTime =
        OffsetDateTime.parse(input, formatter)
    }

  implicit lazy val defaultZonedDateTime: Argument[ZonedDateTime] =
    zonedDateTimeWithFormatter(DateTimeFormatter.ISO_ZONED_DATE_TIME)

  final def zonedDateTimeWithFormatter(formatter: DateTimeFormatter): Argument[ZonedDateTime] =
    new JavaTimeArgument[ZonedDateTime]("ZONED_DATE_TIME") {
      override protected def parseUnsafe(input: String): ZonedDateTime = ZonedDateTime.parse(input, formatter)
    }

}
