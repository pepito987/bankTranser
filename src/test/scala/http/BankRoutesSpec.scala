//package http
//
//import akka.actor.ActorRef
//import org.scalatest.{Matchers, WordSpec}
//import org.scalatest.concurrent.ScalaFutures
//
//class BankRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
//  with BankRoutes {
//
//  override val actorRef: ActorRef =
//    system.actorOf(BankActor.props, "bankActor")
//
//  lazy val routes = bankRoutes
//
//  "BankRoutes" should {
//    "return "
//  }
//
//
//}
