package com.jci.timeseries
package daos
package impl

import com.datastax.driver.core.{Cluster => CassandraCluster, BatchStatement}
import java.time.{ZonedDateTime, ZoneOffset, Instant}
import java.util.Date
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class TimeseriesCassandraDaoImpl(cluster: CassandraCluster, keyspace: String)(implicit ec: ExecutionContext) extends TimeseriesDao {

  val session = cluster.connect(keyspace)
  val createTimeseriesPs = session.prepare("INSERT INTO timeseries (timeseriesId,orgId,timeseriesType,status,dataType) VALUES (?, ?, ?, ?, ?)")
  val getTimeseriesPs = session.prepare("SELECT * FROM timeseries WHERE timeseriesId = ?")
  val changeStatusPs = session.prepare("UPDATE timeseries SET status = :status WHERE timeseriesId = :tid")
  val deleteTimeseriesPs = session.prepare("DELETE FROM timeseries WHERE timeseriesId = ?")

  val createSamplesPs = session.prepare("INSERT INTO samples (timeseriesId,val,timestamp,metric) VALUES (?, ?, ?, ?)")
  val getSamplesPs = session.prepare("SELECT * FROM samples WHERE timeseriesId = ? AND timestamp >= ? AND timestamp < ?")
  val getSamplesWithMetricPs = session.prepare("SELECT * FROM samples WHERE timeseriesId = ?  AND metric = ? AND timestamp >= ? AND timestamp < ? ALLOW FILTERING")

  override def createTimeseries(timeseries: Seq[Timeseries]): Future[Unit] = {
    val batch = timeseries.foldLeft(new BatchStatement())((batch, ts) =>
      batch.add(createTimeseriesPs.bind(ts.timeseriesId, ts.orgId, ts.timeseriesType, ts.status.value, ts.dataType)))
    Future { session.execute(batch); ()}
  }
  override def getTimeseries(timeseriesId: UUID): Future[Option[Timeseries]] = {
    Future {
      val rs = session.execute(getTimeseriesPs.bind(timeseriesId))
      Option(rs.one).map(row => Timeseries(row.getUUID(0), row.getString(1), row.getString(2), TimeseriesStatus.withValue(row.getString(3)), row.getString(4)))
    }
  }
  override def changeActive(timeseriesId: UUID, active: Boolean): Future[Unit] = {
    Future { session.execute(changeStatusPs.bind().setUUID("tid", timeseriesId).setString("status", if (active) "active" else "inactive")); () }
  }
  override def delete(timeseriesId: UUID): Future[Unit] = {
    Future { session.execute(deleteTimeseriesPs.bind(timeseriesId)); () }
  }

  override def createSamples(samples: Seq[Sample]): Future[Unit] = {
    val batch = samples.foldLeft(new BatchStatement())((batch, s) =>
      batch.add(createSamplesPs.bind(s.timeseriesId, s.`val`: java.lang.Double, new Date(s.timestamp.toInstant.toEpochMilli), s.metric)))
    Future { session.execute(batch); () }
  }

  val epochZonedDateTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
  val maxZonedDateTime = ZonedDateTime.of(99999999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  override def getSamples(timeseriesId: UUID, startTime: Option[ZonedDateTime], endTime: Option[ZonedDateTime], metric: Option[String]): Future[Seq[Sample]] = {
    val minTs = startTime.getOrElse(epochZonedDateTime)
    val maxTs = endTime.getOrElse(maxZonedDateTime)
    val st = metric match {
      case Some(m) => getSamplesWithMetricPs.bind(timeseriesId, minTs, maxTs, m)
      case _ => getSamplesPs.bind(timeseriesId, new Date(minTs.toInstant.toEpochMilli), new Date(maxTs.toInstant.toEpochMilli))
    }
    Future {
      val rs = session.execute(st)
      rs.iterator.asScala.map(row =>
        Sample(row.getUUID("timeseriesId"), row.getDouble("val"), ZonedDateTime.ofInstant(row.getTimestamp("timestamp").toInstant, ZoneOffset.UTC),
               row.getString("metric"))).to[Vector]
    }
  }
}
