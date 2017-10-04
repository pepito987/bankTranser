package services


//trait AccountOperations {
//  implicit class AccountWithOperations(account: BankAccount) {
//    def withdraw(amount: BigDecimal): BigDecimal = {
//      val new_balance = account.balance - amount
//      if(new_balance >= 0){
//        db = db.updated(account.id, account.copy(balance = new_balance ))
//        amount
//      } else
//        0
//    }
//  }
//  implicit class Peppe(b: BigDecimal) {
//    def transferTo(account: BankAccount): Unit ={
//      db = db.updated(account.id,account.copy(balance = account.balance + b))
//
//    }
//
//  }
//}
