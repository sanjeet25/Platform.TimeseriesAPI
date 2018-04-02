package com.jci.timeseries

import play.core.server.{AkkaHttpServerProvider, Server, ServerConfig}

/**
 * Custom runnable class in the test scope to run play. Because the default sbt task will always exhaust the metaspace
 * and destroy your sbt console
 */
object Play extends App {

  implicit val serverProvider = new AkkaHttpServerProvider
  Server.withApplicationFromContext(ServerConfig(port = Some(9000), mode = play.api.Mode.Dev))(new TimeseriesAppLoader().load){ port =>
    Thread.sleep(99999999)
  }
}
