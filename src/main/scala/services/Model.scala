package services

case class BankAccount(id: String = "", balance: BigDecimal = 0)

case class WithdrawRequest(from: String, amount: BigDecimal )
case class DepositRequest(to: String, amount: BigDecimal )
case class TransferRequest(from: String, to: String, amount: BigDecimal)

case class ErrorResponse(error: Error)

case class Response(error: Option[Error])

trait Error {
  def errorMessage: String
}

case class InsufficientFund(override val errorMessage: String = "Insufficient Fund") extends Error
case class AccountNotFound(override val errorMessage: String = "Account not found") extends Error
case class AmountNotValid(override val errorMessage: String = "The amount value is not valid") extends Error
case class RequestNotValid(override val errorMessage: String = "The request is not valid") extends Error
