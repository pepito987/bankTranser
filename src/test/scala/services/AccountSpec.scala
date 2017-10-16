package services

import common.ServiceAware
import org.scalatest.Matchers
import spray.json._

import scalaj.http.Http

class AccountSpec extends ServiceAware with Matchers with JsonSupport{

  "GET /account/{id} " should {
    "return 404 if the account with {id} doesn't exist " in {

      val response = Http("http://localhost:8080/account/0000")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].reason shouldBe AccountNotFound().errorMessage
    }

    "return 200 if the account with {id} exist " in {
      val acc = BankAccount("111", 200)
      server.service.accountsDB.put(acc.id, acc)
      val response = Http(s"http://localhost:8080/account/${acc.id}")
        .asString

      response.code shouldBe 200
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[BankAccount] shouldBe acc
    }

    "return error in json" in {
      val response = Http("http://localhost:8080/account/0000")
        .header("Content-Type","application/x-www-form-urlencoded")
        .asString

      response.code shouldBe 404
      response.header("Content-Type").get shouldBe "application/json"
      response.body.parseJson.convertTo[ErrorResponse].reason shouldBe AccountNotFound().errorMessage
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

}
