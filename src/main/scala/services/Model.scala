package services

import org.joda.time.DateTime

case class BankAccount(id: String, userName:String, balance: BigDecimal = 0)

case class Transaction(srcAccount:String, dstAccount:Option[String]= None, amount:BigDecimal)

case class Withdraw(from: String, amount: BigDecimal )
case class Deposit(to: String, amount: BigDecimal )
case class Transfer(from: String, to: String, amount: BigDecimal)

sealed trait TransactionRequest
case class SingleTransaction(amount: BigDecimal) extends TransactionRequest
case class BiTransaction(to: String, amount: BigDecimal) extends TransactionRequest

case class CreateAccountRequest(userName:String, initialBalance:Option[BigDecimal])

case class SuccessTransactionResponse(id:String, balance: BigDecimal)
case class FailedTransactionResponse(id:String, reason: String)
case class TransactionRecordResponse(transactionId:String, accountId:String, amount: Option[BigDecimal] = None, time: DateTime)
case class ErrorResponse(reason: String)

sealed trait TransactionRecord
case class SuccessTransaction(id:String, data: Transaction, time: DateTime)extends TransactionRecord
case class FailedTransaction(id: String, data: Transaction, error: Error, time: DateTime) extends TransactionRecord

sealed trait TransferStatus
case class FailedWithdraw(error: Error) extends TransferStatus
case class FailedDeposit(error: Error) extends TransferStatus

sealed trait Error{
  def errorMessage:String
}
case class InsufficientFund(override val errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val errorMessage: String = "Account not found") extends Error
case class AmountNotValid(override val errorMessage: String = "The amount value is not valid") extends Error
case class TransactionNotFound(override val errorMessage: String = "The the transaction does not exist") extends Error
case class InvalidName(override val errorMessage: String = "Invalid name for the account") extends Error

