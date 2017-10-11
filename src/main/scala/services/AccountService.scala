package services

import java.util.UUID
import services.TransactionStatus


trait AccountService {
  val accountsDB: scala.collection.concurrent.Map[String, BankAccount]
//  val transactionsDB: scala.collection.concurrent.Map[String, Transaction]

  def create(): BankAccount = accountsDB.synchronized {
    val id = UUID.randomUUID().toString
    val account = BankAccount(id = id)
    accountsDB.putIfAbsent(id, account)
      .getOrElse(account)
  }

  def get(id: String): Either[AccountNotFound, BankAccount] = {
    accountsDB.get(id).toRight(AccountNotFound())
  }

  private def execDeposit(to: String, amount: BigDecimal): Either[Error, BankAccount] = {
    //    this.synchronized{
    accountsDB.get(to).map { account =>
      if (account.balance + amount < 0)
        Left(InsufficientFund())
      else {
        val copy = account.copy(balance = account.balance + amount)
        accountsDB.put(to, copy)
        Right(copy)
      }
    }.getOrElse(Left(AccountNotFound()))
    //    }
  }

//  private def storeTransaction(request: TransferRequest, status: TransactionStatus) = {
//    val transactionId = UUID.randomUUID().toString
//    transactionsDB.put(transactionId,Transaction(transactionId,request,status))
//  }

  def withdraw(withdrawRequest: Withdraw): Either[Error, BankAccount] = {
    if (withdrawRequest.amount < 0)
      Left(AmountNotValid())
    else
      execDeposit(withdrawRequest.from, -withdrawRequest.amount)
  }

  def deposit(depositRequest: Deposit): Either[Error, BankAccount] = {
    if (depositRequest.amount < 0)
      Left(AmountNotValid())
    else
      execDeposit(depositRequest.to, depositRequest.amount)
  }

  def transfer(transferRequest: Transfer): Either[Error, BankAccount] = {

    def doWithdraw(transferRequest: Transfer) = {
      execDeposit(transferRequest.from, -transferRequest.amount)
    }

    def doDeposit(transferRequest: Transfer) = {
      execDeposit(transferRequest.to, transferRequest.amount)
    }

    def doRollback(transferRequest: Transfer) = {
      execDeposit(transferRequest.from, transferRequest.amount)
    }

    val failOrAccount = for {
      updatedSrcAccount <- doWithdraw(transferRequest).left.map(err => FailedWithdraw(err))
      _ <- doDeposit(transferRequest).left.map(err => FailedDeposit(err))
    } yield updatedSrcAccount

    failOrAccount match {
      case Left(err: FailedDeposit) => {
        doRollback(transferRequest)
//        storeTransaction(transferRequest,err)
        Left(err.error)
      }
      case Left(err: FailedWithdraw) =>{
//        storeTransaction(transferRequest,err)
        Left(err.error)
      }
      case Right(account) =>{
//        storeTransaction(transferRequest,ValidTransaction)
        Right(account)
      }
    }

  }

}


