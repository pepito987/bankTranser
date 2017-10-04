package http

import services._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import spray.json._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scalaj.http.Http
import spray.json._
import DefaultJsonProtocol._

class AccountSpec extends WordSpec with Matchers with BeforeAndAfter with JsonSupport{

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

//  "Request GET on /accounts " should {
//    "return an empty account list" in {
//
//      val response = Http("http://localhost:8080/accounts").asString
//
//      response.code shouldBe 200
//      response.header("Content-Type") shouldBe Some("application/json")
//      response.body.parseJson shouldBe JsArray.empty
//    }
//
//    "return a json with a string" in {
//      val acc = BankAccount("111",200)
//      server.service.db.put(acc.id, acc)
//
//      val response = Http("http://localhost:8080/accounts").asString
//
//      response.code shouldBe 200
//      response.header("Content-Type") shouldBe Some("application/json")
//      response.body.parseJson shouldBe List(acc.id).toJson
//    }
//  }

  "Request GET on /account/{id} " should {
    "return 404 if the account with {id} doesn't exist " in {

      val response = Http("http://localhost:8080/account/0000").asString

      response.code shouldBe 404
    }

    "return 200 if the account with {id} exist " in {
      val acc = BankAccount("111", 200)
      server.service.db.put(acc.id, acc)
      val response = Http(s"http://localhost:8080/account/${acc.id}").asString

      response.code shouldBe 200
      printf(response.body.parseJson.prettyPrint)
      response.body.parseJson.convertTo[BankAccount] shouldBe acc
    }

  }

  "Request GET on /account " should {

    "return 501  " in {
      val response = Http("http://localhost:8080/account").asString

      response.code shouldBe 501
    }
  }

  "Request Post on /account " should {
    "Create a new account" in {
      val balance = BigDecimal(200)

      val response = Http("http://localhost:8080/account").postData("").asString

      response.code shouldBe 201
      val id = response.body.parseJson.convertTo[String]
      server.service.db.contains(id) shouldBe true
    }

  }

  "Request POST on /withdraw" should {
    "withdraw money from the account" in {

      val acc = BankAccount("123", 200)
      server.service.db.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .postData(WithdrawRequest(acc.id,50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe BankAccount("123",150)
    }
  }

  "Request POST on /transfer" should {
    "return 400 if the body is empty" in {

      val response = Http("http://localhost:8080/transfer")
        .postData("").asString
      response.code shouldBe 400
    }

    "return 400 if the from account do not exist" in {

      val from = "123"
      val to = "987"
      val amount = 50

      val response = Http("http://localhost:8080/transfer")
        .postData(
          TransferRequest(from,to,amount).toJson.toString()
        ).asString

      response.code shouldBe 400
      //Error message in the body?

    }

    "return 200 and the balance reflect the transfer" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.db.put(from, BankAccount(from,200))
      server.service.db.put(to, BankAccount(to,200))

      val response = Http("http://localhost:8080/transfer")
        .postData(
          TransferRequest(from,to,amount).toJson.toString()
        ).asString

      response.code shouldBe 200
      val fromAcc = server.service.db.get(from).get
      val toAcc = server.service.db.get(to).get

      fromAcc.balance shouldBe 150
      toAcc.balance shouldBe 250

      //Error message in the body?

    }
  }

}
