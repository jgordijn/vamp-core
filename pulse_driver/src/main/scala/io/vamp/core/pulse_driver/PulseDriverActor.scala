package io.vamp.core.pulse_driver

import java.time.OffsetDateTime

import _root_.io.vamp.common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.pulse.api.Event
import io.vamp.core.model.artifact.{Port, Deployment, DeploymentCluster}
import io.vamp.core.pulse_driver.notification.{PulseDriverNotificationProvider, PulseResponseError, UnsupportedPulseDriverRequest}

import scala.concurrent.duration._

object PulseDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.pulse.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[PulseDriverActor], args: _*)

  trait PulseDriverMessage

  case class EventExists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime) extends PulseDriverMessage

  case class ResponseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime) extends PulseDriverMessage

  case class QuerySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime)

}

class PulseDriverActor(driver: PulseDriver) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with PulseDriverNotificationProvider {

  import io.vamp.core.pulse_driver.PulseDriverActor._

  implicit val timeout = PulseDriverActor.timeout

  override protected def requestType: Class[_] = classOf[PulseDriverMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case event: Event => offLoad(driver.event(event), classOf[PulseResponseError])
      case EventExists(deployment, cluster, from) => offLoad(driver.exists(deployment, cluster, from), classOf[PulseResponseError])
      case ResponseTime(deployment, cluster, port, from, to) => offLoad(driver.responseTime(deployment, cluster, port, from, to), classOf[PulseResponseError])
      case QuerySlaEvents(deployment, cluster, from, to) => offLoad(driver.querySlaEvents(deployment, cluster, from, to), classOf[PulseResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => exception(PulseResponseError(e))
  }
}