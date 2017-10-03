package services

import java.util.UUID

trait AccountService {
  val accountDBConnector: AccountDBConnector = ???

  def create(): Account = {
    val id = UUID.randomUUID().toString
    //Todo change the .get
    accountDBConnector.add(BankAccount(id = id)).get
  }
}
