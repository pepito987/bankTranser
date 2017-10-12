package services

import common.ServiceAware
import org.scalatest.Matchers
import spray.json._
import scalaj.http.Http

class TransactionWithdrawSpec extends ServiceAware with Matchers with JsonSupport {

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

}
