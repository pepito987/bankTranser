package services

import scala.collection.mutable


trait AccountDBConnector {
  def add(account: BankAccount): Option[BankAccount]
  def find(id:String): Option[BankAccount]
}

trait MapDBConnector extends AccountDBConnector{
  val db: mutable.Map[String, BankAccount]

  override def add(account: BankAccount): Option[BankAccount] = db.synchronized {
    account match {
      case BankAccount(id, balance ) if balance >=0 => db.put(id,account)
    }
  }

  override def find(id: String) = {
    db.get(id)
  }


}
