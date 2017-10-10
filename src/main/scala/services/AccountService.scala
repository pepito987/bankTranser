package services

import java.util.UUID


trait AccountService {
  val db: scala.collection.concurrent.Map[String,BankAccount]

  def create(): BankAccount = db.synchronized {
    val id = UUID.randomUUID().toString
    val account = BankAccount(id = id)
    db.putIfAbsent(id,account)
      .getOrElse(account)
  }

  def get(id: String): Either[AccountNotFound, BankAccount] = {
    db.get(id).toRight(AccountNotFound())
  }

  private def execDeposit(to: String, amount: BigDecimal) = {
    //    this.synchronized{
    db.get(to).map{ account =>
      if(account.balance + amount < 0)
        Left(InsufficientFund())
      else{
        val copy = account.copy(balance = account.balance + amount)
        db.put(to,copy)
        Right(copy)
      }
    }.getOrElse(Left(AccountNotFound()))
    //    }
  }

  def withdraw(withdrawRequest: WithdrawRequest): Either[Error, BankAccount] = {
    if(withdrawRequest.amount < 0)
      Left(AmountNotValid())
    else
      execDeposit(withdrawRequest.from, -withdrawRequest.amount)
  }

  def deposit(depositRequest: DepositRequest): Either[Error, BankAccount] = {
    if(depositRequest.amount < 0)
      Left(AmountNotValid())
    else
      execDeposit(depositRequest.to,depositRequest.amount)
  }

  def transfer(transferRequest: TransferRequest): Either[Error, Option[BankAccount]] = {
    for {
      account <- execDeposit(transferRequest.from,-transferRequest.amount)
      _ <- execDeposit(transferRequest.to,transferRequest.amount)
    } yield Some(account)

  }
}

