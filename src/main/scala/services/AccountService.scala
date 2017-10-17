package services

import java.util.UUID

import org.joda.time.DateTime
import org.slf4j.LoggerFactory


trait AccountService {
  val logger = LoggerFactory.getLogger(classOf[AccountService])
  val accountsDB: scala.collection.concurrent.Map[String, BankAccount]
  val transactionsDB: scala.collection.concurrent.Map[String, TransactionRecord]

  def create(userName:String, balance:Option[BigDecimal]): Either[Error, BankAccount] = accountsDB.synchronized {
    val isEmptyName = userName.isEmpty
    val isNegative: Option[Boolean] = balance.map{ _ <= 0}

    (isEmptyName, isNegative) match {
      case (true, _ ) => Left(InvalidName())
      case (_, Some(true)) => Left(AmountNotValid())
      case (_,_) => {
        val id = UUID.randomUUID().toString
        val account = BankAccount(id = id, userName=userName, balance=balance.getOrElse(0))
        logger.debug(s"Creating account [$id]")
        Right(accountsDB.putIfAbsent(id, account).getOrElse(account))
      }
    }
  }

  def getAccount(id: String): Option[BankAccount] = {
    accountsDB.get(id)
  }

  def getTransaction(transactionId:String, accountId:String): Either[Error, TransactionRecord] = {
    for {
      _ <- getAccount(accountId).toRight(AccountNotFound())
      tx <- transactionsDB.get(transactionId).toRight(TransactionNotFound())
    } yield tx
  }

  def getTransactions(accId: String): Either[AccountNotFound, List[FetchTransactionResponse]] ={
    accountsDB.get(accId).map{ _ =>
      val records: List[FetchTransactionResponse] = transactionsDB.values
        .filter(_.account == accId)
        .map {
          case tx: SuccessTransaction => FetchTransactionResponse(tx.id, tx.account, Some(tx.balance), tx.time)
          case tx: FailedTransaction => FetchTransactionResponse(tx.id, tx.account, time = tx.time)
        }.toList
      Right(records)
    }.getOrElse(Left(AccountNotFound()))
  }

  private def execDeposit(accountId: String, amount: BigDecimal): Either[Error, BankAccount] = {
    this.synchronized{
      accountsDB.get(accountId).map { account =>
        if (account.balance + amount < 0)
          Left(InsufficientFund())
        else {
          logger.debug(s"Executing deposit on account [$accountId] of amount [$amount]")
          val copy = account.copy(balance = account.balance + amount)
          accountsDB.put(accountId, copy)
          Right(copy)
        }
      }.getOrElse(Left(AccountNotFound()))
    }
  }

  private def storeFailTransaction(account:String, request: TransactionType, error: Error) = {
    logger.debug(s"Storing failed Transaction: [$request] with error: [$error]")
    val transaction = FailedTransaction(UUID.randomUUID().toString,account,request,error, DateTime.now())
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  private def storeSuccessTransaction(account:String,request: TransactionType, balance: BigDecimal) = {
    logger.debug(s"Storing successful transaction: [$request] with balance: [$balance]")
    val transaction = SuccessTransaction(UUID.randomUUID().toString,account, request,balance, DateTime.now())
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  def withdraw(withdrawRequest: Withdraw): TransactionRecord = {
    if (withdrawRequest.amount < 0){
      storeFailTransaction(withdrawRequest.from,withdrawRequest,AmountNotValid())
    }
    else{
      execDeposit(withdrawRequest.from, -withdrawRequest.amount) match {
        case Right(account) =>{
          storeSuccessTransaction(withdrawRequest.from,withdrawRequest,account.balance)
        }
        case Left(error) => {
          storeFailTransaction(withdrawRequest.from,withdrawRequest,error)
        }
      }
    }
  }

  def deposit(depositRequest: Deposit): TransactionRecord = {
    if (depositRequest.amount < 0)
      storeFailTransaction(depositRequest.to,depositRequest,AmountNotValid())
    else{
      execDeposit(depositRequest.to, depositRequest.amount) match {
        case Right(account) => {
          storeSuccessTransaction(depositRequest.to,depositRequest,account.balance)
        }
        case Left(err) => storeFailTransaction(depositRequest.to,depositRequest,err)
      }

    }
  }

  def transfer(transferRequest: Transfer): TransactionRecord = {

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
        storeFailTransaction(transferRequest.from,transferRequest,err.error)
      }
      case Left(err: FailedWithdraw) =>{
        storeFailTransaction(transferRequest.from,transferRequest,err.error)
      }
      case Right(account) =>{
        storeSuccessTransaction(transferRequest.from,transferRequest,account.balance)
      }
    }
  }



}


