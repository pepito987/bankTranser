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

  "GET /account/{id} " should {
    "return 404 if the account with {id} doesn't exist " in {

      val response = Http("http://localhost:8080/account/0000")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[Response].error.get.errorMessage shouldBe AccountNotFound().errorMessage
    }

    "return 200 if the account with {id} exist " in {
      val acc = BankAccount("111", 200)
      server.service.accountsDB.put(acc.id, acc)
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
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage
    }

  }

  "GET /account " should {

    "return 501 if empty request " in {
      val response = Http("http://localhost:8080/account")
        .header("Content-Type","application/json")
        .asString

      response.code shouldBe 501
      response.header("Content-Type").get shouldBe "application/json"
    }
  }

  "Post /account " should {
    "Create a new account" in {
      val response = Http("http://localhost:8080/account")
        .header("Content-Type","application/json")
        .postData("")
        .asString

      response.code shouldBe 201
      response.header("Content-Type").get shouldBe "application/json"
      val account = response.body.parseJson.convertTo[BankAccount]
      server.service.accountsDB.contains(account.id) shouldBe true
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
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage

    }

    "return 400 if the amount is bigger then the balance" in {
      val acc = BankAccount("123", 50)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest(acc.id,100).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe InsufficientFund().errorMessage
    }

    "return 200 if the withdraw is permitted" in {

      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest(acc.id,50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe BankAccount("123",150)
    }

    "return 400 if the amount is negative" in {
      val acc = BankAccount("123", 50)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/withdraw")
        .header("Content-Type","application/json")
        .postData(WithdrawRequest(acc.id,-100).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AmountNotValid().errorMessage

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
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage

    }

    "return 400 if the amount is negative" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/deposit")
        .header("Content-Type","application/json")
        .postData(DepositRequest(acc.id,-50).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AmountNotValid().errorMessage

    }

    "return 200 if the deposit is possible" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

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
