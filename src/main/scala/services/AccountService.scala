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

  def getTransactions(accId: String): Either[AccountNotFound, List[TransactionRecordResponse]] ={
    accountsDB.get(accId).map{ _ =>
      val records: List[TransactionRecordResponse] = transactionsDB.values
          .collect{
            case tx: SuccessTransaction if tx.data.srcAccount == accId => TransactionRecordResponse(tx.id, tx.data.srcAccount, Some(tx.data.amount), tx.time)
            case tx: FailedTransaction if tx.data.srcAccount == accId => TransactionRecordResponse(tx.id, tx.data.srcAccount, time = tx.time)
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

  private def storeFailTransaction(request: Transaction, error: Error) = {
    logger.debug(s"Storing failed Transaction [$request] with error: [$error]")
    val transaction = FailedTransaction(UUID.randomUUID().toString, request, error, DateTime.now())
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  private def storeSuccessTransaction(request: Transaction) = {
    logger.debug(s"Storing successful transaction [$request]")
    val transaction = SuccessTransaction(UUID.randomUUID().toString, request, DateTime.now())
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  def withdraw(withdrawRequest: Withdraw): TransactionRecord = {
    if (withdrawRequest.amount < 0){
      storeFailTransaction(Transaction(withdrawRequest.from, amount = withdrawRequest.amount),AmountNotValid())
    }
    else{
      execDeposit(withdrawRequest.from, -withdrawRequest.amount) match {
        case Right(account) =>{
          storeSuccessTransaction(Transaction(withdrawRequest.from, amount = withdrawRequest.amount))
        }
        case Left(error) => {
          storeFailTransaction(Transaction(withdrawRequest.from, amount = withdrawRequest.amount),error)
        }
      }
    }
  }

  def deposit(depositRequest: Deposit): TransactionRecord = {
    if (depositRequest.amount < 0)
      storeFailTransaction(Transaction(depositRequest.to, amount = depositRequest.amount),AmountNotValid())
    else{
      execDeposit(depositRequest.to, depositRequest.amount) match {
        case Right(account) => {
          storeSuccessTransaction(Transaction(depositRequest.to, amount = depositRequest.amount))
        }
        case Left(err) => storeFailTransaction(Transaction(depositRequest.to, amount = depositRequest.amount),err)
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
        storeFailTransaction(Transaction(transferRequest.from, Some(transferRequest.to), amount = transferRequest.amount),err.error)
      }
      case Left(err: FailedWithdraw) =>{
        storeFailTransaction(Transaction(transferRequest.from, Some(transferRequest.to), amount = transferRequest.amount),err.error)
      }
      case Right(account) =>{
        storeSuccessTransaction(Transaction(transferRequest.from, Some(transferRequest.to), amount = transferRequest.amount))
      }
    }
  }



}


