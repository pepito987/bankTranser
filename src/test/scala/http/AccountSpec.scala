package http

import services._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

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

//  "GET on /accounts " should {
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

  "GET /account/{id} " should {
    "return 404 if the account with {id} doesn't exist " in {

      val response = Http("http://localhost:8080/account/0000")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
    }

    "return 200 if the account with {id} exist " in {
      val acc = BankAccount("111", 200)
      server.service.db.put(acc.id, acc)
      val response = Http(s"http://localhost:8080/account/${acc.id}")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe acc
    }

    "return error in json" in {
      val response = Http("http://localhost:8080/account/0000")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.err_msg shouldBe AccountNotFound().err_msg
    }

  }

  "GET /account " should {

    "return 501  " in {
      val response = Http("http://localhost:8080/account")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 501
      response.header("Content-Type").get shouldBe "application/json"
    }
  }

  "Post /account " should {
    "Create a new account" in {
      val balance = BigDecimal(200)

      val response = Http("http://localhost:8080/account")
        .header("Content-Type","application/json")
        .postData("")
        .asString

      response.code shouldBe 201
      response.header("Content-Type").get shouldBe "application/json"
      val id = response.body.parseJson.convertTo[String]
      server.service.db.contains(id) shouldBe true
    }

  }

  "POST /withdraw" should {
    "return 404 if the account does not exist" in {

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest("111",50).toJson.toString())
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.err_msg shouldBe AccountNotFound().err_msg

    }

    "return 400 if the amount is bigger then the balance" in {
      val acc = BankAccount("123", 50)
      server.service.db.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest(acc.id,100).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.err_msg shouldBe InsufficientFund().err_msg
    }

    "return 200 if the withdraw is permitted" in {

      val acc = BankAccount("123", 200)
      server.service.db.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest(acc.id,50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe BankAccount("123",150)
    }

  }

  "Post on /deposit" should {
    "return 404 if account not found" in {
      val response = Http("http://localhost:8080/deposit")
        .header("Content-Type","application/json")
        .postData(DepositRequest("111",50).toJson.toString())
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.err_msg shouldBe AccountNotFound().err_msg

    }

    "return 400 if the amount is negative" in {
      val acc = BankAccount("123", 200)
      server.service.db.put(acc.id, acc)

      val response = Http("http://localhost:8080/deposit")
        .header("Content-Type","application/json")
        .postData(DepositRequest(acc.id,-50).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.err_msg shouldBe AmountNotValid().err_msg

    }

    "return 200 if the deposit is possible" in {
      val acc = BankAccount("123", 200)
      server.service.db.put(acc.id, acc)

      val response = Http("http://localhost:8080/deposit")
        .header("Content-Type","application/json")
        .postData(DepositRequest(acc.id,50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe BankAccount("123",250)
    }
  }



}
