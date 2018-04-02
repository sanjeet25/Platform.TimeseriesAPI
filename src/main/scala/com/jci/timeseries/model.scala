package com.jci.timeseries

import java.time.ZonedDateTime
import java.util.UUID
import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract class TimeseriesStatus(val value: String) extends StringEnumEntry
object TimeseriesStatus extends StringEnum[TimeseriesStatus] {
  val values = findValues
  case object Active extends TimeseriesStatus("active")
  case object Inactive extends TimeseriesStatus("inactive")
}

case class Timeseries(timeseriesId: UUID, orgId: String, timeseriesType: String, status: TimeseriesStatus, dataType: String)

case class Sample(timeseriesId: UUID, `val`: Double, timestamp: ZonedDateTime, metric: String)