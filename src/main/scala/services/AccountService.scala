package services

import java.util.UUID

import org.slf4j.LoggerFactory


trait AccountService {
  val logger = LoggerFactory.getLogger(classOf[AccountService])
  val accountsDB: scala.collection.concurrent.Map[String, BankAccount]
  val transactionsDB: scala.collection.concurrent.Map[String, Transaction]

  def create(): BankAccount = accountsDB.synchronized {
    val id = UUID.randomUUID().toString
    val account = BankAccount(id = id)
    logger.debug(s"Creating account [$id]")
    accountsDB.putIfAbsent(id, account)
      .getOrElse(account)
  }

  def getAccount(id: String): Either[AccountNotFound, BankAccount] = {
    accountsDB.get(id).toRight(AccountNotFound())
  }

  def getTransaction(id:String): Either[TransactionNotFound, Transaction] = {
    transactionsDB.get(id).toRight(TransactionNotFound())
  }

  private def execDeposit(accountId: String, amount: BigDecimal): Either[Error, BankAccount] = {
    logger.debug(s"Executing deposit on account [$accountId] of amount [$amount]")
    this.synchronized{
      accountsDB.get(accountId).map { account =>
        if (account.balance + amount < 0)
          Left(InsufficientFund())
        else {
          val copy = account.copy(balance = account.balance + amount)
          accountsDB.put(accountId, copy)
          Right(copy)
        }
      }.getOrElse(Left(AccountNotFound()))
    }
  }

  private def storeFailTransaction(request: TransactionRequest, error: Error) = {
    logger.debug(s"Storing failed Transaction: [$request] with error: [$error]")
    val transaction = FailedTransaction(UUID.randomUUID().toString,request,error)
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  private def storeSuccessTransaction(request: TransactionRequest, balance: BigDecimal) = {
    logger.debug(s"Storing successful transaction: [$request] with balance: [$balance]")
    val transaction = SuccessTransaction(UUID.randomUUID().toString,request,balance)
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  def withdraw(withdrawRequest: Withdraw): Transaction = {
    if (withdrawRequest.amount < 0){
      storeFailTransaction(withdrawRequest,AmountNotValid())
    }
    else{
      execDeposit(withdrawRequest.from, -withdrawRequest.amount) match {
        case Right(account) =>{
          storeSuccessTransaction(withdrawRequest,account.balance)
        }
        case Left(error) => {
          storeFailTransaction(withdrawRequest,error)
        }
      }
    }
  }

  def deposit(depositRequest: Deposit): Transaction = {
    if (depositRequest.amount < 0)
      storeFailTransaction(depositRequest,AmountNotValid())
    else{
      execDeposit(depositRequest.to, depositRequest.amount) match {
        case Right(account) => {
          storeSuccessTransaction(depositRequest,account.balance)
        }
        case Left(err) => storeFailTransaction(depositRequest,err)
      }

    }
  }

  def transfer(transferRequest: Transfer): Transaction = {

    def doWithdraw(transferRequest: Transfer) = {
      if(transferRequest.amount <0)
        Left(AmountNotValid())
      else
        execDeposit(transferRequest.from, -transferRequest.amount)
    }

    def doDeposit(transferRequest: Transfer) = {
      execDeposit(transferRequest.to, transferRequest.amount)
    }

    def doRollback(transferRequest: Transfer) = {
      execDeposit(transferRequest.from, transferRequest.amount)
    }

    val transferStatus: Either[TransferStatus, BankAccount] = for {
      updatedSrcAccount <- doWithdraw(transferRequest).left.map(err => FailedWithdraw(err))
      _ <- doDeposit(transferRequest).left.map(err => FailedDeposit(err))
    } yield updatedSrcAccount

    transferStatus match {
      case Left(err: FailedDeposit) => {
        logger.info(s"Failed transaction with Rollback on request: [$transferRequest]")
        doRollback(transferRequest)
        storeFailTransaction(transferRequest,err.error)
      }
      case Left(err: FailedWithdraw) =>{
        storeFailTransaction(transferRequest,err.error)
      }
      case Right(account) =>{
        storeSuccessTransaction(transferRequest,account.balance)
      }
    }
  }



}


