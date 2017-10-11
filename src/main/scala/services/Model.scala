package services

case class BankAccount(id: String = "", balance: BigDecimal = 0)

trait TransactionRequest
case class Withdraw(from: String, amount: BigDecimal ) extends TransactionRequest
case class Deposit(to: String, amount: BigDecimal ) extends TransactionRequest
case class Transfer(from: String, to: String, amount: BigDecimal) extends TransactionRequest

case class ErrorResponse(error: Error)

case class Response(error: Option[Error])

case class Transaction(id: String, request: Transfer, status: TransactionStatus)

trait TransactionStatus
case object ValidTransaction extends TransactionStatus
case class FailedWithdraw(error: Error) extends TransactionStatus
case class FailedDeposit(error: Error) extends TransactionStatus

trait Error {
  def errorMessage: String
}

case class InsufficientFund(override val errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val errorMessage: String = "Account not found") extends Error
case class AmountNotValid(override val errorMessage: String = "The amount value is not valid") extends Error
case class RequestNotValid(override val errorMessage: String = "The request is not valid") extends Error
