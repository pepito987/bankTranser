package services

import common.ServiceAware
import org.scalatest.Matchers
import spray.json._
import scalaj.http.Http

class TransactionWithdrawSpec extends ServiceAware with Matchers with JsonSupport {

  "POST /account/{id}/withdraw" should {
    "return 404 if the account does not exist and store the transaction" in {

      val withdrawRequest = SingleTransaction(50)

      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason shouldBe AccountNotFound().errorMessage
      server.service.transactionsDB.values.
        collect{case x:FailedTransaction => x}
        .find(tx => tx.request.isInstanceOf[Withdraw])
        .get.request.asInstanceOf[Withdraw].amount shouldBe withdrawRequest.amount
    }

    "return 400 if the amount is bigger then the balance" in {
      val acc = BankAccount("123", "bob", 50)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(SingleTransaction(100).toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[FailedTransactionResponse].reason shouldBe InsufficientFund().errorMessage
    }

    "return 200 if the withdraw is permitted and store the transaction" in {

      val acc = BankAccount("123", "bob", 200)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = SingleTransaction(50)
      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val body = response.body.parseJson.convertTo[SuccessTransactionResponse]
      body.balance shouldBe 150
      server.service.transactionsDB.contains(body.id) shouldBe true
    }

    "return a transaction id if valid withdraw and store the transaction" in {
      val acc = BankAccount("123", "bob", 200)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = SingleTransaction(50)
      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val body = response.body.parseJson.convertTo[SuccessTransactionResponse]
      body.id.length should be >0

      server.service.transactionsDB.contains(body.id) shouldBe true
    }

    "return 400 if the amount is negative and store the transaction and store the transaction" in {
      val acc = BankAccount("123", "bob", 50)
      server.service.accountsDB.put(acc.id, acc)

      val withdrawRequest = SingleTransaction(-100)

      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(withdrawRequest.toJson.toString())
        .asString

      response.code shouldBe 400
      response.header("Content-Type").get shouldBe "application/json"
      val body = response.body.parseJson.convertTo[FailedTransactionResponse]
      body.reason shouldBe AmountNotValid().errorMessage

      server.service.transactionsDB.contains(body.id) shouldBe true
    }

    "store the successful withdraw transaction" in {
      val acc = BankAccount("123", "bob", 200)
      server.service.accountsDB.put(acc.id, acc)

      val response = Http("http://localhost:8080/account/123/withdraw")
        .header("Content-Type","application/json")
        .postData(SingleTransaction(50).toJson.toString())
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      val transaction = response.body.parseJson.convertTo[SuccessTransactionResponse]
      transaction.balance shouldBe 150
      server.service.transactionsDB.contains(transaction.id) shouldBe true
    }

  }

}
