package services

import java.util.UUID

import scala.collection.mutable

abstract class Account
case class BankAccount(id: String = "", balance: BigDecimal = 0) extends Account

abstract class Request
case class WithdrawRequest(from: String, amount: BigDecimal ) extends Request
case class TransferRequest(from: String, to: String, amount: BigDecimal) extends Request

sealed trait Error {
  def err_msg: String
}
//TODO bad choice
case class InsufficientFund(override val err_msg: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val err_msg: String = "Account not found") extends Error
case class InternalError(override val err_msg: String = "Internal Error") extends Error




trait AccountService {
  val db: mutable.Map[String,BankAccount]

  def create(): Option[String] = db.synchronized {
    val id = UUID.randomUUID().toString
    db.put(id, BankAccount(id = id)).map(_.id).orElse(Some(id))
  }

  def get(id: String): Either[AccountNotFound, BankAccount] = {
    db.get(id) match {
      case Some(x) => Right(x)
      case _ => Left(AccountNotFound())
    }
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
      case _ => Left(InsufficientFund())
    }
  }

  def transfer(transferRequest: TransferRequest): Either[Error, Option[BankAccount]] = db.synchronized {

    def makeTransfer(from: BankAccount, to: BankAccount, amount: BigDecimal) = {
      val src = db.put(from.id, from.copy(balance = from.balance - transferRequest.amount))
      val dst = db.put(to.id, to.copy(balance = to.balance + transferRequest.amount))
      src
    }

    val src = db.get(transferRequest.from)
    val dst = db.get(transferRequest.to)

    (src, dst) match {
      case (Some(from), Some(to)) if from.balance >= transferRequest.amount => Right(makeTransfer(from,to, transferRequest.amount))
      case (_, _) => Left(InsufficientFund())
      case _ => Left(AccountNotFound())
    }

  }
}

