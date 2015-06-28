package io.vamp.core.router_driver

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import io.vamp.common.notification.NotificationErrorException
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Port}
import io.vamp.core.router_driver.notification.{RouterResponseError, UnsupportedRouterDriverRequest}
import org.scalatest.concurrent.{Futures, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}

import scala.concurrent.Future

object RouterDriverActorSpec {
  sealed trait ImplementationVersionUnderTest
  case object NewImplementation extends ImplementationVersionUnderTest
  case object OldImplementation extends ImplementationVersionUnderTest
}

abstract class RouterDriverActorSpec(version: RouterDriverActorSpec.ImplementationVersionUnderTest, _system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with Futures with ScalaFutures with BeforeAndAfterEach {
  import RouterDriverActorSpec._
  def this(version: RouterDriverActorSpec.ImplementationVersionUnderTest, actorSystemName: String) = this(version, ActorSystem(actorSystemName, ConfigFactory.parseString("vamp.core.router-driver.response-timeout = 10")))
  val services = List(RouteService("foo", 100, List(Server("name", "host", 123)), Nil))
  val clusterRoutes = List(ClusterRoute({(x,y,z)=>true},1243, services))
  val endpointRoutes: List[EndpointRoute] = List(EndpointRoute({(a,b) => true}, 23323, services))
  val deploymentRoutes = DeploymentRoutes(clusterRoutes, endpointRoutes)
  val exception = new scala.IllegalArgumentException("wrong")

  var error = false

  override def beforeEach() = {
    error = false
  }

  val driver: RouterDriver = new RouterDriver {

    def all: Future[DeploymentRoutes] = if(error) Future.failed(exception) else Future.successful(deploymentRoutes)
    def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any] = if(error) Future.failed(exception) else Future.successful("removed")
    def remove(deployment: Deployment, port: Port): Future[Any] = if(error) Future.failed(exception) else Future.successful("removed2")
    def info: Future[Any] = if(error) Future.failed(exception) else Future.successful("info")
    def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean): Future[Any] = if(error) Future.failed(exception) else Future.successful("create")
    def create(deployment: Deployment, port: Port, update: Boolean): Future[Any] = if(error) Future.failed(exception) else Future.successful("create2")
  }
  val actor = version match {
    case NewImplementation => system.actorOf(Props(new RouterDriverActor(driver)))
    case OldImplementation => system.actorOf(Props(new RouterDriverActorOld(driver)))
  }

  val deployment: Deployment = Deployment("someDeploy", List(DeploymentCluster("cluster1",Nil, None, Map.empty, Map.empty)), Nil, Nil, Nil, Nil, Nil)
  val cluster: DeploymentCluster = DeploymentCluster("deployCluster", Nil, None, Map.empty, Map.empty)
  val port = Port.portFor(231)

  "RouterDriverActor" should {
    "reply on inforRequest by calling driver" in {
      actor ! InfoRequest
      expectMsg("info")
    }
    "reply on inforRequest by calling driver error" in {
      error = true
      actor ! InfoRequest
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "handle remove" in {
      actor ! RouterDriverActor.Remove(deployment, cluster, port)
      expectMsg("removed")
    }
    "handle remove error" in {
      error = true
      actor ! RouterDriverActor.Remove(deployment, cluster, port)
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "handle removeEndpoint" in {
      actor ! RouterDriverActor.RemoveEndpoint(deployment, port)
      expectMsg("removed2")
    }
    "handle removeEndpoint error" in {
      error = true
      actor ! RouterDriverActor.RemoveEndpoint(deployment, port)
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "handle Create" in {
      actor ! RouterDriverActor.Create(deployment, cluster, port, true)
      expectMsg("create")
    }
    "handle Create error" in {
      error = true
      actor ! RouterDriverActor.Create(deployment, cluster, port, true)
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "handle CreateEndpoint" in {
      actor ! RouterDriverActor.CreateEndpoint(deployment, port, true)
      expectMsg("create2")
    }
    "handle CreateEndpoint error" in {
      error = true
      actor ! RouterDriverActor.CreateEndpoint(deployment, port, true)
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "handle All" in {
      actor ! RouterDriverActor.All
      expectMsg(deploymentRoutes)
    }
    "handle All error" in {
      error = true
      actor ! RouterDriverActor.All
      expectMsg(NotificationErrorException(RouterResponseError(exception), "Router response error."))
    }
    "give unsupportedOperation" in {
      case class NotExpected(msg: String)
      actor ! NotExpected("foo")
      val msg = expectMsgType[akka.actor.Status.Failure]
      val notificationErrorException = msg.cause.asInstanceOf[NotificationErrorException]
      notificationErrorException.message should be ("Unsupported router driver request.")
      notificationErrorException.notification shouldBe UnsupportedRouterDriverRequest(NotExpected("foo"))
    }
  }
}



