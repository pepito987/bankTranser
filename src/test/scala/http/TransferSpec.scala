package http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import services.{AccountNotFound, _}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scalaj.http.Http


class TransferSpec extends WordSpec with Matchers with BeforeAndAfter with JsonSupport with ScalaFutures {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  var server: Server = null

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(1000, Millis)), scaled(Span(15, Millis)))


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
        .header("Content-Type", "application/json")
        .postData("").asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe RequestNotValid().errorMessage
    }

    "return 404 if the from account does not exist" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(to, BankAccount(to, 200))

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type", "application/json")
        .postData(
          Transfer(from, to, amount).toJson.toString()
        ).asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage
    }

    "return 404 if the dst account does not exist" in {
      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type", "application/json")
        .postData(
          Transfer(from, to, amount).toJson.toString()
        ).asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe AccountNotFound().errorMessage
    }

    "rollback if the deposit on a transaction fails" in {
      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type", "application/json")
        .postData(
          Transfer(from, to, amount).toJson.toString()
        ).asString

      response.code shouldBe 404

      server.service.accountsDB(from).balance shouldBe 200

    }

    "return 400 if the amount is bigger than the balance and will not update the accounts" in {

      val amount = 500
      val from = BankAccount("123", 200)
      val to = BankAccount("987", 200)

      server.service.accountsDB.put(from.id, from)
      server.service.accountsDB.put(to.id, to)

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type", "application/json")
        .postData(
          Transfer(from.id, to.id, amount).toJson.toString()
        ).asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].error.errorMessage shouldBe InsufficientFund().errorMessage

      server.service.accountsDB(from.id) shouldBe from
      server.service.accountsDB(to.id) shouldBe to
    }

    "return 200 and the balance reflect the transfer" in {

      val from = "123"
      val to = "987"
      val amount = 50

      server.service.accountsDB.put(from, BankAccount(from, 200))
      server.service.accountsDB.put(to, BankAccount(to, 200))

      val response = Http("http://localhost:8080/transfer")
        .header("Content-Type", "application/json")
        .postData(
          Transfer(from, to, amount).toJson.toString()
        ).asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[SuccessTransactionResponse].balance shouldBe 150
      val fromAcc = server.service.accountsDB(from)
      val toAcc = server.service.accountsDB(to)

      fromAcc.balance shouldBe 150
      toAcc.balance shouldBe 250
    }

    "handle concurrency" in {

      for( _ <- 1 until 200) {
        server.service.accountsDB.put("bob", BankAccount("bob", 200))
        server.service.accountsDB.put("alice", BankAccount("alice", 200))
        server.service.accountsDB.put("john", BankAccount("john", 200))

        val requests = scala.util.Random.shuffle(List(
          Transfer("bob", "alice", 50),
          Transfer("alice", "john", 50)))

        val responses = requests.map(r => Future {
          Http("http://localhost:8080/transfer")
            .header("Content-Type", "application/json")
            .postData(
              r.toJson.toString()
            ).asString
        })

        Future.sequence(responses).futureValue.foreach(response => {
          response.code shouldBe 200
          response.header("Content-Type").get shouldBe "application/json"
        })

        server.service.accountsDB("alice").balance shouldBe 200
        server.service.accountsDB("bob").balance shouldBe 150
        server.service.accountsDB("john").balance shouldBe 250
      }

    }
  }

}
