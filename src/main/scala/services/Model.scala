package services

import org.joda.time.DateTime

case class BankAccount(id: String = "", balance: BigDecimal = 0)

sealed trait TransactionRequest
case class Withdraw(from: String, amount: BigDecimal ) extends TransactionRequest
case class Deposit(to: String, amount: BigDecimal ) extends TransactionRequest
case class Transfer(from: String, to: String, amount: BigDecimal) extends TransactionRequest

case class SuccessTransactionResponse(id:String, balance: BigDecimal)
case class FailedTransactionResponse(id:String, reason: String)
case class FetchTransactionResponse(id:String, balance: Option[BigDecimal] = None, time: DateTime)
case class ErrorResponse(reason: String)

sealed trait Transaction
case class SuccessTransaction(id:String, request: TransactionRequest, balance: BigDecimal, time: DateTime)extends Transaction
case class FailedTransaction(id: String, request: TransactionRequest, error: Error, time: DateTime) extends Transaction

sealed trait TransferStatus
case class FailedWithdraw(error: Error) extends TransferStatus
case class FailedDeposit(error: Error) extends TransferStatus

sealed trait Error
case class InsufficientFund(errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(errorMessage: String = "Account not found") extends Error
case class AmountNotValid(errorMessage: String = "The amount value is not valid") extends Error
case class RequestNotValid(errorMessage: String = "The request is not valid") extends Error
case class TransactionNotFound(errorMessage: String = "The the transaction does not exist") extends Error

