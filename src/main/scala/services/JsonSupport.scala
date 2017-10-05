package services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val accountJsonFormat = jsonFormat2(BankAccount)
  implicit val withdrawRequestFormat = jsonFormat2(WithdrawRequest)
  implicit val transferRequestFormat = jsonFormat3(TransferRequest)
  implicit val insufficientFundFormat = jsonFormat1(InsufficientFund)
  implicit val accountNotFoundFormat = jsonFormat1(AccountNotFound)
  implicit val internalErrorFormat = jsonFormat1(InternalError)

}