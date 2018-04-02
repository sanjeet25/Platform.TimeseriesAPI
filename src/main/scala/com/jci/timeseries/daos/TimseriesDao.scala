package com.jci.timeseries
package daos

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.Future

trait TimeseriesDao {

  def createTimeseries(timeseries: Seq[Timeseries]): Future[Unit]
  def getTimeseries(timeseriesId: UUID): Future[Option[Timeseries]]
  def changeActive(timeseriesId: UUID, active: Boolean): Future[Unit]
  def delete(timeseriesId: UUID): Future[Unit]

  def createSamples(samples: Seq[Sample]): Future[Unit]
  def getSamples(timeseriesId: UUID, startTime: Option[ZonedDateTime], endTime: Option[ZonedDateTime], metric: Option[String]): Future[Seq[Sample]]
}
