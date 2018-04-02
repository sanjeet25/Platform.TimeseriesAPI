package com.jci.timeseries.controllers

import java.time.ZonedDateTime
import play.api.mvc.PathBindable.Parsing

object Utils {

  implicit val instantQueryPathBindable = new Parsing[ZonedDateTime](
    parse = ZonedDateTime.parse,
    serialize = _.toString,
    error = { (key, e) => "Cannot parse parameter %s as ZonedDateTime: %s".format(key, e.getMessage) }
  )
}
