package account

import scala.collection.mutable


trait AccountDBConnector {
  def add(account: Account): Option[Account]
}

trait MapDBConnector extends AccountDBConnector{
  val db: mutable.Map[String, Account]

  def add(account: Account): Option[Account] = {
    account match {
      case BankAccount(id, balance ) if balance >=0 => db.put(id,account)
    }
  }

}