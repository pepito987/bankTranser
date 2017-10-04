package services

import java.util.UUID

import scala.collection.mutable

class Account

case class WithdrawRequest(from: String, amount: BigDecimal )
case class TransferRequest(from: String, to: String, amount: BigDecimal)

case class BankAccount(id: String = "", balance: BigDecimal = 0) extends Account

class Request
case object CreateAccountRequest extends Request

trait Error
case class InsufficientFund(msg: String) extends Error

trait AccountService {
  val db: mutable.Map[String,BankAccount]

  def create(): Option[String] = db.synchronized {
    val id = UUID.randomUUID().toString
    db.put(id, BankAccount(id = id)).map(_.id).orElse(Some(id))
  }

  def get(id: String): Option[BankAccount] = {
    db.get(id)
  }

  def withdraw(withdrawRequest: WithdrawRequest): Either[InsufficientFund, BankAccount] = db.synchronized {
    val account = db(withdrawRequest.from)
    val new_balance = account.balance - withdrawRequest.amount
    new_balance match {
      case x if x >=0 => {
        val copy = account.copy(balance = new_balance)
        db.put(account.id, copy)
        Right(copy)
      }
      case _ => Left(InsufficientFund("Insufficient found"))
    }
  }

  def transfer(transferRequest: TransferRequest): Either[String, (Option[BankAccount], Option[BankAccount])] = db.synchronized {

    def makeTransfer(from: BankAccount, to: BankAccount, amount: BigDecimal) = {
      val a = db.put(from.id, from.copy(balance = from.balance - transferRequest.amount))
      val b = db.put(to.id, to.copy(balance = to.balance + transferRequest.amount))
      (a,b)
    }

    val src = db.get(transferRequest.from)
    val dst = db.get(transferRequest.to)

    (src, dst) match {
      case (Some(from), Some(to)) => Right(makeTransfer(from,to, transferRequest.amount))
      case _ => Left("Account not valid")
    }

  }
}

