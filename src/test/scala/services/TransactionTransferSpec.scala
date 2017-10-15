package services

import java.util.UUID

import common.ServiceAware
import org.scalatest.Matchers
import org.scalatest.concurrent.{Futures, ScalaFutures, ScaledTimeSpans}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaj.http.{Http, HttpResponse}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import duration._


class TransactionTransferSpec extends ServiceAware with ScaledTimeSpans with Futures with Matchers with JsonSupport with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)),interval = scaled(Span(500,Millis)))

  "POST /transaction/transfer" should {
    "return 400 if the body is empty and log the transaction" in {

      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData("").asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].reason shouldBe RequestNotValid().errorMessage
    }

    "return 404 if the from account does not exist and store the transaction" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(to, BankAccount(to, 200))

      val transfer = Transfer(from, to, amount)
      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData(
          transfer.toJson.toString()
        ).asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason shouldBe AccountNotFound().errorMessage

      server.service.transactionsDB.values
        .collect{case x:FailedTransaction => x}
        .find(tx => tx.request == transfer)
    }

    "return 404 if the dst account does not exist" in {
      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))

      val transfer = Transfer(from, to, amount)
      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData(
          transfer.toJson.toString()
        ).asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason shouldBe AccountNotFound().errorMessage

      server.service.transactionsDB.values
        .collect{case x:FailedTransaction => x}
        .find(tx => tx.request == transfer)
    }

    "rollback if the deposit on a transaction fails" in {
      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))

      val transfer = Transfer(from, to, amount)
      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData(
          transfer.toJson.toString()
        ).asString

      response.code shouldBe 404
      server.service.accountsDB(from).balance shouldBe 200

      server.service.transactionsDB.values
        .collect{case x:FailedTransaction => x}
        .find(tx => tx.request == transfer)

    }

    "return 400 if the amount is bigger than the balance and will not update the accounts" in {

      val amount = 500
      val from = BankAccount("123", 200)
      val to = BankAccount("987", 200)

      server.service.accountsDB.put(from.id, from)
      server.service.accountsDB.put(to.id, to)

      val transfer = Transfer(from.id, to.id, amount)
      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData(
          transfer.toJson.toString()
        ).asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason shouldBe InsufficientFund().errorMessage

      server.service.accountsDB(from.id) shouldBe from
      server.service.accountsDB(to.id) shouldBe to

      server.service.transactionsDB.values
        .collect{case x:FailedTransaction => x}
        .find(tx => tx.request == transfer)
    }

    "return 200 and the balance reflect the transfer" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))
      server.service.accountsDB.put(to, BankAccount(to, 200))

      val transfer = Transfer(from, to, amount)
      val response = Http("http://localhost:8080/transaction/transfer")
        .header("Content-Type", "application/json")
        .postData(
          transfer.toJson.toString()
        ).asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[SuccessTransactionResponse].balance shouldBe 150
      val fromAcc = server.service.accountsDB(from)
      val toAcc = server.service.accountsDB(to)

      fromAcc.balance shouldBe 150
      toAcc.balance shouldBe 250

      server.service.transactionsDB.values
        .collect{case x:FailedTransaction => x}
        .find(tx => tx.request == transfer)
    }

    "handle concurrency" in {

      case class MyException(mgs:String) extends Exception

      def sendRequest(transfer: Transfer): Future[HttpResponse[String]] = Future {
        Http("http://localhost:8080/transaction/transfer")
          .header("Content-Type", "application/json")
          .postData(
            transfer.toJson.toString()
          ).asString
      }.transform{
        case Success(x) if x.code == 503 => Failure(new MyException(""))
        case Success(y) => Success(y)
      }
        .recoverWith{
          case e:MyException => sendRequest(transfer)
        }


      /*
          bob   ->30 alice : bob= 170 alice = 230
          alice ->25 john  : alice= 205 john = 225
       */



      for( _ <- 1 until 200) {
        server.service.accountsDB.put("bob", BankAccount("bob", 200))
        server.service.accountsDB.put("alice", BankAccount("alice", 200))
        server.service.accountsDB.put("john", BankAccount("john", 200))

        val requests = scala.util.Random.shuffle(List(
          Transfer("bob", "alice", 30),
          Transfer("alice", "john", 25)))

        val responses: Seq[Future[HttpResponse[String]]] = requests.map(r => sendRequest(r))

        Future.sequence(responses).futureValue.foreach(response => {
          response.code shouldBe 200
          response.header("Content-Type").get shouldBe "application/json"
        })

        println(server.service.accountsDB)
        server.service.accountsDB("alice").balance shouldBe 205
        server.service.accountsDB("bob").balance shouldBe 170
        server.service.accountsDB("john").balance shouldBe 225
      }
    }
  }

  "GET on /transaction/tx/{id} " should {
    "return a transaction " in {

      val uuid = UUID.randomUUID().toString
      val tx = SuccessTransaction(uuid,Transfer("111", "222", 90),87)

      server.service.transactionsDB.put(tx.id,tx)

      val response = Http(s"http://localhost:8080/transaction/tx?id=${uuid}")
        .header("Content-Type", "application/x-www-form-urlencoded").asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[SuccessTransactionResponse].balance shouldBe 87
    }
  }

}
