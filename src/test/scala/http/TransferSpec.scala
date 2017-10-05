package http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import services.{BankAccount, JsonSupport, TransferRequest}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scalaj.http.Http


class TransferSpec extends WordSpec with Matchers with BeforeAndAfter with JsonSupport{
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  var server: Server = null

  before {
    server = new Server
    Await.ready(server.start, Duration.Inf )
  }

  after {
    Await.ready(server.stop, Duration.Inf)
  }

  "POST /transfer" should {
    "return 400 if the body is empty" in {

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type","application/json")
        .postData("").asString

      response.code shouldBe 400
      println(response.body)
      response.header("Content-Type").get shouldBe "application/json"
    }

    "return 400 if the from account do not exist" in {

      val from = "123"
      val to = "987"
      val amount = 50

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type","application/json")
        .postData(
          TransferRequest(from,to,amount).toJson.toString()
        ).asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      //Error message in the body?

    }

    "return 200 and the balance reflect the transfer" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.db.put(from, BankAccount(from,200))
      server.service.db.put(to, BankAccount(to,200))

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type","application/json")
        .postData(
          TransferRequest(from,to,amount).toJson.toString()
        ).asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"

      val fromAcc = server.service.db.get(from).get
      val toAcc = server.service.db.get(to).get

      fromAcc.balance shouldBe 150
      toAcc.balance shouldBe 250

      //Error message in the body?
    }

    "return 400 if the amount is bigger than the balance and will not update the accounts" in {

      val amount = 500
      val from = BankAccount("123",200)
      val to = BankAccount("987",200)

      server.service.db.put(from.id, from )
      server.service.db.put(to.id, to )

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type","application/json")
        .postData(
          TransferRequest(from.id,to.id,amount).toJson.toString()
        ).asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"

      server.service.db.get(from.id).get shouldBe from
      server.service.db.get(to.id).get shouldBe to

    }

  }

}
