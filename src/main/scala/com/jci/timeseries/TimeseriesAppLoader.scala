package com.jci.timeseries

import com.datastax.driver.core.{Cluster => CassandraCluster}
import com.jci.timeseries.daos.impl.TimeseriesCassandraDaoImpl
import java.time.ZonedDateTime
import java.util.UUID
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.routing.Router
import play.api.routing.sird._
import play.filters.HttpFiltersComponents
//import router.Routes

class TimeseriesAppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach(_.configure(context.environment, context.initialConfiguration, Map.empty))
    new TimeseriesComponents(context).application
  }
}

class TimeseriesComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context)
  with HttpFiltersComponents {

  val cluster = CassandraCluster.builder.
    addContactPoint(configuration.get[String]("cassandra.contactPoint")).
    withPort(configuration.get[Int]("cassandra.port")).
    build()

  val timeseriesDao = new TimeseriesCassandraDaoImpl(cluster, configuration.get[String]("cassandra.keyspace"))(
    actorSystem.dispatchers.lookup("cassandra.threadpool"))
  val timeseries = new controllers.TimeseriesController(controllerComponents, timeseriesDao)

  import controllers.Utils.instantQueryPathBindable
  val uuid = new PathBindableExtractor[UUID]
  val instant = new PathBindableExtractor[ZonedDateTime]

  lazy val router = Router.from {
    case POST(p"/timeseries/new" ? q_?"n=${int(n)}") => timeseries.create(n getOrElse 1)
    case GET(p"/timeseries/${uuid(tid)}") => timeseries.get(tid)
    case POST(p"/timeseries/${uuid(tid)}/status/inactive") => timeseries.changeActive(tid, false)
    case POST(p"/timeseries/${uuid(tid)}/status/active") => timeseries.changeActive(tid, true)
    case DELETE(p"/timeseries/${uuid(tid)}") => timeseries.delete(tid)

    case POST(p"/samples") => timeseries.createSamples(None)
    case POST(p"/${uuid(tid)}/samples") => timeseries.createSamples(Some(tid))
    case GET(p"/${uuid(tid)}/samples" ? q_?"startTime=${instant(startTime)}" & q_?"endTime=${instant(endTime)}" & q_?"metric=$metric") =>
      timeseries.getSamples(tid, startTime, endTime, metric)
  }
}