package services

case class BankAccount(id: String = "", balance: BigDecimal = 0)

trait TransactionRequest
case class Withdraw(from: String, amount: BigDecimal ) extends TransactionRequest
case class Deposit(to: String, amount: BigDecimal ) extends TransactionRequest
case class Transfer(from: String, to: String, amount: BigDecimal) extends TransactionRequest

case class SuccessTransactionResponse(id:String, balance: BigDecimal)
case class ErrorResponse(error: Error)

case class SuccessTransaction(id: String, request: TransactionRequest, balance: BigDecimal)
case class FailedTransaction(id: String, request: TransactionRequest, error: Error)

case class Response(error: Option[Error])

trait TransactionStatus
case class FailedWithdraw(error: Error) extends TransactionStatus
case class FailedDeposit(error: Error) extends TransactionStatus

trait Error {
  def errorMessage: String
}

case class InsufficientFund(override val errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val errorMessage: String = "Account not found") extends Error
case class AmountNotValid(override val errorMessage: String = "The amount value is not valid") extends Error
case class RequestNotValid(override val errorMessage: String = "The request is not valid") extends Error
