package account

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val accountJsonFormat = jsonFormat2(BankAccount)
  implicit val withdrawRequestFormat = jsonFormat2(WithdrawRequest)
}


class Account

case class WithdrawRequest(from: String, amount: BigDecimal )

case class BankAccount(id: String = "", balance: BigDecimal = 0) extends Account


class Request
case object CreateAccountRequest extends Request


trait Db {
  var db: Map[String,BankAccount]
}

trait AccountOperations extends Db{
  implicit class AccountWithOperations(account: BankAccount) {
    def withdraw(amount: BigDecimal): BigDecimal = {
      val new_balance = account.balance - amount
      if(new_balance >= 0){
        db = db.updated(account.id, account.copy(balance = new_balance ))
        amount
      } else
        0
    }
  }
  implicit class Peppe(b: BigDecimal) {
    def transferTo(account: BankAccount): Unit ={
      db = db.updated(account.id,account.copy(balance = account.balance + b))

    }

  }
}
