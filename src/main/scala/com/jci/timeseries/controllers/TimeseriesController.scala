package com.jci.timeseries
package controllers

import com.jci.timeseries.daos.TimeseriesDao
import java.time.ZonedDateTime
import java.util.UUID
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes, JsString}
import play.api.mvc.{AbstractController, ControllerComponents, Result}
import scala.concurrent.{Future, ExecutionContext}


object TimeseriesController {

  implicit val timeseriesStatusReads = Reads.StringReads.map(s => TimeseriesStatus.withValue(s))
  implicit val timeseriesStatusWrites = Writes[TimeseriesStatus](s => JsString(s.value))
  implicit val timeseriesFormat = Json.format[Timeseries]
  implicit val sampleWithTimeseriesFormat = Json.format[Sample]

  case class CreateTimeseries(orgId: Option[String], timeseriesType: Option[String], status: Option[TimeseriesStatus], dataType: Option[String])
  implicit val createTimeseriesFormat = Json.format[CreateTimeseries]

  case class PartialSample(`val`: Double, timestamp: ZonedDateTime)
  implicit val partialSampleFormat = Json.format[PartialSample]
}

class TimeseriesController(cc: ControllerComponents, timeseriesDao: TimeseriesDao)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  import TimeseriesController._

  val logger = Logger(getClass)

  implicit class OnErrorLog(val f: Future[Result]) {
    def onErrorLog(msg: String): Future[Result] = f.recover { case ex =>
      logger.error(msg, ex)
      InternalServerError("Something went wrong")
    }
  }

  private val baseTimeseriesTemplate = Timeseries(null, "jci.com", "None", TimeseriesStatus.Active, "double")
  def create(n: Int) = Action.async { request =>

    val errorEitherTemplate = if (request.hasBody) {
      request.body.asJson.map(Json.fromJson[CreateTimeseries](_)).toRight(left = Future.successful(UnsupportedMediaType)).flatMap {
        case JsSuccess(template, _) =>
          Right(Timeseries(null, template.orgId.getOrElse(baseTimeseriesTemplate.orgId),
                           template.timeseriesType.getOrElse(baseTimeseriesTemplate.timeseriesType),
                           template.status.getOrElse(baseTimeseriesTemplate.status),
                           template.dataType.getOrElse(baseTimeseriesTemplate.dataType)))
        case JsError(errors) => Left(Future successful BadRequest(Json.obj("error" -> "Invalid json format")))
      }
    } else {
      Right(baseTimeseriesTemplate)
    }

    errorEitherTemplate.map { template =>
      val newContainers = Iterator.continually(template.copy(timeseriesId = UUID.randomUUID())).take(n).to[Vector]
      timeseriesDao.createTimeseries(newContainers).map { _ =>
        if (n == 1) Ok(Json.toJson(newContainers(0)))
        else Ok(Json.toJson(newContainers))
      }.onErrorLog("failed creating new timeseries containers")
    }.merge
  }

  def get(timeseriesId: UUID) = Action.async {
    timeseriesDao.getTimeseries(timeseriesId).map(cs => Ok(Json.toJson(cs))).
      onErrorLog(s"failed retrieving timeseries $timeseriesId")
  }

  def changeActive(timeseriesId: UUID, active: Boolean) = Action.async {
    timeseriesDao.changeActive(timeseriesId, active).map(_ => NoContent).
      onErrorLog(s"failed changing status for timeseries $timeseriesId")
  }

  def delete(timeseriesId: UUID) = Action.async {
    timeseriesDao.delete(timeseriesId).map(_ => NoContent).
      onErrorLog(s"failed deleting timeseries $timeseriesId")
  }

  def createSamples(timeseriesId: Option[UUID]) = Action.async(parse.json) { request =>
    val samples = timeseriesId.fold(request.body.validate[Seq[Sample]])(tid =>
      request.body.validate[Seq[PartialSample]].map(_.map(s => Sample(tid, s.`val`, s.timestamp, "Raw"))))

    samples match {
      case JsSuccess(samples, path) =>

        val uniqueTimeseries = samples.groupBy(_.timeseriesId).keySet
        Future.sequence(uniqueTimeseries map (t => timeseriesDao.getTimeseries(t) map (t -> _))).flatMap { containers =>
          val (inactive, notFound) = containers.foldLeft[(Seq[UUID], Seq[UUID])]((Vector.empty, Vector.empty)) {
            case ((inactive, notFound), (tid, None)) => (inactive, notFound :+ tid)
            case ((inactive, notFound), (tid, Some(ts))) if ts.status == TimeseriesStatus.Inactive => (inactive :+ tid, notFound)
            case (state, _) => state
          }
          if (inactive.nonEmpty || notFound.nonEmpty) {
            var err = Json.obj("error" -> "invalid timeseries found")
            if (notFound.nonEmpty) err = err + ("missingTimeseries" -> Json.toJson(notFound))
            if (inactive.nonEmpty) err = err + ("inactiveTimeseries" -> Json.toJson(inactive))
            //use NotFound if we were specified a timeseriesid in the path
            timeseriesId.fold(Future.successful(NotFound(err)))(_ => Future.successful(BadRequest(err)))
          } else {
            timeseriesDao.createSamples(samples).map(_ => NoContent).
              onErrorLog(s"failed creating samples")
          }
        }

      case JsError(errors) =>
        logger.warn("Invalid json format\n" + errors.mkString("\n"))
        Future successful BadRequest(Json.obj("error" -> "Invalid json format"))
    }
  }

  def getSamples(timeseriesId: UUID, startTime: Option[ZonedDateTime], endTime: Option[ZonedDateTime], metric: Option[String]) = Action.async {
    logger.info(s"fetching samples for $timeseriesId")
    timeseriesDao.getSamples(timeseriesId, startTime, endTime, metric).
      map(samples => Ok(Json.toJson(samples))).
      onErrorLog(s"failed obtaining samples for $timeseriesId, startTime:$startTime, endTime:$endTime, metric:$metric")
  }
}

