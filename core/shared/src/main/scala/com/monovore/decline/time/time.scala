package com.monovore.decline

import java.time._
import java.time.format.DateTimeFormatter

package object time {

  implicit lazy val defaultZoneId: Argument[ZoneId] =
    new JavaTimeArgument[ZoneId]("zone-id") {
      override protected def parseUnsafe(input: String): ZoneId = ZoneId.of(input)
    }

  implicit lazy val defaultZoneOffset: Argument[ZoneOffset] =
    new JavaTimeArgument[ZoneOffset]("zone-offset") {
      override protected def parseUnsafe(input: String): ZoneOffset = ZoneOffset.of(input)
    }

  implicit lazy val defaultDuration: Argument[Duration] =
    new JavaTimeArgument[Duration]("iso-duration") {
      override protected def parseUnsafe(input: String): Duration = Duration.parse(input)
    }

  implicit lazy val defaultPeriod: Argument[Period] =
    new JavaTimeArgument[Period]("iso-period") {
      override protected def parseUnsafe(input: String): Period = Period.parse(input)
    }

  implicit lazy val defaultInstant: Argument[Instant] = new JavaTimeArgument[Instant]("iso-date-time") {
    override protected def parseUnsafe(input: String): Instant =
      Instant.parse(input)
  }

  final def monthDayWithFormatter(formatter: DateTimeFormatter, metavar: String = "month-day"): Argument[MonthDay] =
    new JavaTimeArgument[MonthDay](metavar) {
      override protected def parseUnsafe(input: String): MonthDay =
        MonthDay.parse(input, formatter)
    }

  final def yearWithFormatter(formatter: DateTimeFormatter, metavar: String = "year"): Argument[Year] =
    new JavaTimeArgument[Year](metavar) {
      override protected def parseUnsafe(input: String): Year =
        Year.parse(input, formatter)
    }

  final def yearMonthWithFormatter(formatter: DateTimeFormatter, metavar: String = "year-month"): Argument[YearMonth] =
    new JavaTimeArgument[YearMonth](metavar) {
      override protected def parseUnsafe(input: String): YearMonth =
        YearMonth.parse(input, formatter)
    }

  implicit lazy val defaultLocalDate: Argument[LocalDate] =
    localDateWithFormatter(DateTimeFormatter.ISO_LOCAL_DATE, metavar = "iso-local-date")

  final def localDateWithFormatter(formatter: DateTimeFormatter, metavar: String = "local-date"): Argument[LocalDate] =
    new JavaTimeArgument[LocalDate](metavar) {
      override protected def parseUnsafe(input: String): LocalDate =
        LocalDate.parse(input, formatter)
    }

  implicit lazy val defaultLocalTime: Argument[LocalTime] =
    localTimeWithFormatter(DateTimeFormatter.ISO_LOCAL_TIME, metavar = "iso-local-time")

  final def localTimeWithFormatter(formatter: DateTimeFormatter, metavar: String = "local-time"): Argument[LocalTime] =
    new JavaTimeArgument[LocalTime](metavar) {
      override protected def parseUnsafe(input: String): LocalTime =
        LocalTime.parse(input, formatter)
    }

  implicit lazy val defaultLocalDateTime: Argument[LocalDateTime] =
    localDateTimeWithFormatter(DateTimeFormatter.ISO_LOCAL_DATE_TIME, metavar = "iso-local-date-time")

  final def localDateTimeWithFormatter(formatter: DateTimeFormatter, metavar: String = "local-date-time"): Argument[LocalDateTime] =
    new JavaTimeArgument[LocalDateTime](metavar) {
      override protected def parseUnsafe(input: String): LocalDateTime =
        LocalDateTime.parse(input, formatter)
    }

  implicit lazy val defaultOffsetTime: Argument[OffsetTime] =
    offsetTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_TIME, metavar = "iso-offset-time")

  final def offsetTimeWithFormatter(formatter: DateTimeFormatter, metavar: String = "offset-time"): Argument[OffsetTime] =
    new JavaTimeArgument[OffsetTime](metavar) {
      override protected def parseUnsafe(input: String): OffsetTime =
        OffsetTime.parse(input, formatter)
    }

  implicit lazy val defaultOffsetDateTime: Argument[OffsetDateTime] =
    offsetDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME, metavar = "iso-offset-date-time")

  final def offsetDateTimeWithFormatter(formatter: DateTimeFormatter, metavar: String = "offset-date-time"): Argument[OffsetDateTime] =
    new JavaTimeArgument[OffsetDateTime](metavar) {
      override protected def parseUnsafe(input: String): OffsetDateTime =
        OffsetDateTime.parse(input, formatter)
    }

  implicit lazy val defaultZonedDateTime: Argument[ZonedDateTime] =
    zonedDateTimeWithFormatter(DateTimeFormatter.ISO_ZONED_DATE_TIME, metavar = "iso-zoned-date-time")

  final def zonedDateTimeWithFormatter(formatter: DateTimeFormatter, metavar: String = "zoned-date-time"): Argument[ZonedDateTime] =
    new JavaTimeArgument[ZonedDateTime](metavar) {
      override protected def parseUnsafe(input: String): ZonedDateTime = ZonedDateTime.parse(input, formatter)
    }

}
