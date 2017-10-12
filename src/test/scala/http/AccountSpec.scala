package http

import services.{BankAccount, _}
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

      val response = Http("http://localhost:8080/account?id=0000")
        .header("Content-Type","application/x-www-form-urlencoded")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage
    }

    "return 200 if the account with {id} exist " in {
      val acc = BankAccount("111", 200)
      server.service.accountsDB.put(acc.id, acc)
      val response = Http(s"http://localhost:8080/account?id=${acc.id}")
        .header("Content-Type","application/x-www-form-urlencoded")
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe acc
    }

    "return error in json" in {
      val response = Http("http://localhost:8080/account?id=0000")
        .header("Content-Type","application/x-www-form-urlencoded")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage
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
    "return 404 if the account does not exist and store the transaction" in {

      val withdrawRequest = Withdraw("111", 50)
      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      println(response.body)

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason.errorMessage shouldBe AccountNotFound().errorMessage
      server.service.transactionsDB.values.
        collect{case x:FailedTransaction => x}
        .find(tx => tx.request == withdrawRequest).get.request shouldBe withdrawRequest
    }

    "return 400 if the amount is bigger then the balance" in {
      val acc = BankAccount("123", 50)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(Withdraw(acc.id,100).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason.errorMessage shouldBe InsufficientFund().errorMessage
    }

    "return 200 if the withdraw is permitted and store the transaction" in {

      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = Withdraw(acc.id, 50)
      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val body = response.body.parseJson.convertTo[SuccessTransactionResponse]
      body.balance shouldBe 150
      server.service.transactionsDB.values.
        collect{case x:SuccessTransaction => x}
        .find(tx => tx.request == withdrawRequest).get.request shouldBe withdrawRequest
    }

    "return a transaction id if valid withdraw and store the transaction" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = Withdraw(acc.id, 50)
      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[SuccessTransactionResponse].id.length should be >0

      server.service.transactionsDB.values.
        collect{case x:SuccessTransaction => x}
        .find(tx => tx.request == withdrawRequest).get.request shouldBe withdrawRequest
    }

    "return 400 if the amount is negative and store the transaction and store the transaction" in {
      val acc = BankAccount("123", 50)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = Withdraw(acc.id,-100)

      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason.errorMessage shouldBe AmountNotValid().errorMessage

      server.service.transactionsDB.values.
        collect{case x:FailedTransaction => x}
        .find(tx => tx.request == withdrawRequest).get.request shouldBe withdrawRequest
    }

    "store the successful withdraw transaction" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/transaction/withdraw")
        .header("Content-Type","application/json")
        .postData(Withdraw(acc.id,50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val transaction = response.body.parseJson.convertTo[SuccessTransactionResponse]
      transaction.balance shouldBe 150
      server.service.transactionsDB.contains(transaction.id) shouldBe true
    }

  }

  "Post on /deposit" should {
    "return 404 if account not found and store the transaction" in {
      val depositRequest = Deposit("111", 50)
      val response = Http("http://localhost:8080/transaction/deposit")
        .header("Content-Type","application/json")
        .postData(depositRequest.toJson.toString())
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason.errorMessage shouldBe AccountNotFound().errorMessage

      server.service.transactionsDB.values.
        collect{case x:FailedTransaction => x}
        .find(tx => tx.request == depositRequest).get.request shouldBe depositRequest
    }

    "return 400 if the amount is negative and store the transaction" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val depositRequest = Deposit(acc.id, -50)
      val response = Http("http://localhost:8080/transaction/deposit")
        .header("Content-Type","application/json")
        .postData(depositRequest.toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason.errorMessage shouldBe AmountNotValid().errorMessage

      server.service.transactionsDB.values.
        collect{case x:FailedTransaction => x}
        .find(tx => tx.request == depositRequest).get.request shouldBe depositRequest
    }

    "return 200 if the deposit is possible and store the transaction" in {
      val acc = BankAccount("123", 200)
      server.service.accountsDB.put(acc.id, acc)

      val depositRequest = Deposit(acc.id, 50)
      val response = Http("http://localhost:8080/transaction/deposit")
        .header("Content-Type","application/json")
        .postData(depositRequest.toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val tx = response.body.parseJson.convertTo[SuccessTransactionResponse]
      tx.balance shouldBe 250

      server.service.transactionsDB.values.
        collect{case x:SuccessTransaction => x}
        .find(tx => tx.request == depositRequest).get.request shouldBe depositRequest

    }

  }



}
