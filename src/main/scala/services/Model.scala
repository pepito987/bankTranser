package services

import org.joda.time.DateTime

case class BankAccount(id: String = "", balance: BigDecimal = 0)

sealed trait TransactionType
case class Withdraw(from: String, amount: BigDecimal ) extends TransactionType
case class Deposit(to: String, amount: BigDecimal ) extends TransactionType
case class Transfer(from: String, to: String, amount: BigDecimal) extends TransactionType

sealed trait TransactionRequest
case class SingleTransaction(amount: BigDecimal) extends TransactionRequest
case class Transaction(to: String, amount: BigDecimal) extends TransactionRequest

case class SuccessTransactionResponse(id:String, balance: BigDecimal)
case class FailedTransactionResponse(id:String, reason: String)

case class FetchTransactionResponse(transactionId:String, accountId:String, balance: Option[BigDecimal] = None, time: DateTime)
case class ErrorResponse(reason: String)

sealed trait TransactionStatus
case class SuccessTransaction(id:String, request: TransactionType, balance: BigDecimal, time: DateTime)extends TransactionStatus
case class FailedTransaction(id: String, request: TransactionType, error: Error, time: DateTime) extends TransactionStatus

sealed trait TransferStatus
case class FailedWithdraw(error: Error) extends TransferStatus
case class FailedDeposit(error: Error) extends TransferStatus

sealed trait Error{
  def errorMessage:String
}
case class InsufficientFund(override val errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val errorMessage: String = "Account not found") extends Error
case class AmountNotValid(override val errorMessage: String = "The amount value is not valid") extends Error
case class RequestNotValid(override val errorMessage: String = "The request is not valid") extends Error
case class TransactionNotFound(override val errorMessage: String = "The the transaction does not exist") extends Error

