package http

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.util.Timeout
import http.BankActor.GetHello

import scala.concurrent.Future
import scala.concurrent.duration._

trait BankRoutes extends _JsonSupport{
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  // other dependencies that UserRoutes use
  def actorRef: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  lazy val bankRoutes: Route =
    pathPrefix("bank"){
      get{
        val user: Future[Option[User]] =
          (actorRef ? GetHello).mapTo[Option[User]]
        complete(user)
      }
    }

}
