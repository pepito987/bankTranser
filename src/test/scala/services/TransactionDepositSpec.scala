package services

import common.ServiceAware
import org.scalatest.Matchers
import spray.json._

import scalaj.http.Http

class TransactionDepositSpec extends ServiceAware with Matchers with JsonSupport{

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
