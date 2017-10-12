package common

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import http.Server
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import services.JsonSupport

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait ServiceAware extends WordSpec with BeforeAndAfter {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  var server: Server = null

  before {
    server = new Server
    Await.ready(server.start, Duration.Inf)
  }

  after {
    Await.ready(server.stop, Duration.Inf)
  }

}