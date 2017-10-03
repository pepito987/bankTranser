package http

import akka.actor.{Actor, Props}
import akka.actor.Actor.Receive
import http.BankActor.GetHello


final case class User(name: String, age: Int)

object BankActor {
  final case object GetHello
  def props: Props = Props[BankActor]
}

class BankActor extends Actor  {

  var users = Set(User("Peppe",30))

  override def receive: Receive = {
    case GetHello =>
      sender() ! users.find(_.name == "Peppe")
  }
}
