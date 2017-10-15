package services

import java.util.UUID
import services.TransferStatus


trait AccountService {
  val accountsDB: scala.collection.concurrent.Map[String, BankAccount]
  val transactionsDB: scala.collection.concurrent.Map[String, Transaction]
  val txDB: scala.collection.concurrent.Map[String, BankAccount]

  def create(): BankAccount = accountsDB.synchronized {
    val id = UUID.randomUUID().toString
    val account = BankAccount(id = id)
    accountsDB.putIfAbsent(id, account)
      .getOrElse(account)
  }

  def getAccount(id: String): Either[AccountNotFound, BankAccount] = {
    accountsDB.get(id).toRight(AccountNotFound())
  }

  def getTransaction(id:String) = {
    transactionsDB.get(id).toRight(TransactionNotFound())
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

  private def commit(acc1: BankAccount, status: Boolean) ={
    if(status){
      if( txDB.remove(acc1.id,acc1))
        accountsDB.replace(acc1.id,acc1.copy(txLock = None))
      }
  }
//    }
//    else{
//      txDB.remove(acc1.id,acc1)
//      None
//    }
//
//  }

  private def openTransaction(to:String, txId:String): Either[Error, BankAccount] ={
    accountsDB.get(to).map{ account =>
      txDB.putIfAbsent(account.id, account.copy(txLock = Some(txId))).map{ txAcc =>
        txAcc.txLock.map{ lock =>
          if( lock == txId)
            Right(txAcc)
          else
            Left(TransactionNotApplicable)
        }.getOrElse(Right(account.copy(txLock = Some(txId))))
      }.getOrElse(Right(account.copy(txLock = Some(txId))))
    }.getOrElse(Left(AccountNotFound()))
  }

  private def execConcurrentDeposit(account:BankAccount, amount:BigDecimal, txId:String): Either[Error, BankAccount] = {
    account.txLock match {
      case Some(lock) if lock == txId && account.balance + amount >= 0 => {
        val copy = account.copy(balance = account.balance + amount)
        txDB.replace(account.id, copy)
        Right(copy)
      }
      case Some(_) if account.balance + amount< 0  => Left(InsufficientFund())
      case _ => Left(TransactionNotApplicable)
    }
  }

  private def removeIf(id:String, txid: String) ={
    txDB.get(id).map{acc =>
      if(acc.txLock.get == txid)
        txDB.remove(acc.id,acc)
    }
  }

  def concurrentTransfer(request: Transfer): Transaction ={
    val txId = UUID.randomUUID().toString

    val status: Either[Error, BankAccount] = for{
      txFrom <- openTransaction(request.from,txId)
      txTo <- openTransaction(request.to,txId)
      withdrawFrom <- execConcurrentDeposit(txFrom,-request.amount,txId).left.map(err => FailedWithdraw(err))
      depositTo <- execConcurrentDeposit(txTo, request.amount,txId).left.map(err => FailedDeposit(err))
    } yield (withdrawFrom, depositTo)  match {
      case (a,b) => {
        commit(a,status = true)
        commit(b,status = true)
        withdrawFrom
      }
    }

    status match {
      case Left(err: FailedDeposit) => {
        openTransaction(request.from,txId).map{ fromAcc =>
          execConcurrentDeposit(fromAcc,request.amount,txId)
          commit(fromAcc,status = false)
          FailedTransaction(txId,request,err)
        }.getOrElse({
          FailedTransaction(txId,request,FatalError)
        })
      }
      case Left(err: FailedWithdraw) => {
//        openTransaction(request.from,txId).map{ fromAcc =>
//          commit(fromAcc,status=false)
//        }
        FailedTransaction(txId,request,err.error)
      }
      case Left(err) => {
//        openTransaction(request.from,txId).map{ fromAcc =>
//          commit(fromAcc,false)
//        }
//        openTransaction(request.to,txId).map{ toAcc =>
//          commit(toAcc,false)
//        }
        removeIf(request.from,txId)
        removeIf(request.to,txId)
        FailedTransaction(txId,request,err)
      }
      case Right(x) => SuccessTransaction(txId,request,x.balance)
    }


  }


  private def storeFailTransaction(request: TransactionRequest, error: Error) = {
    val transaction = FailedTransaction(UUID.randomUUID().toString,request,error)
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  private def storeSuccessTransaction(request: TransactionRequest, balance: BigDecimal) = {
    val transaction = SuccessTransaction(UUID.randomUUID().toString,request,balance)
    transactionsDB.putIfAbsent(transaction.id,transaction)
    transaction
  }

  def transfer(transferRequest: Transfer): Transaction = {

    def doWithdraw(transferRequest: Transfer) = {
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


