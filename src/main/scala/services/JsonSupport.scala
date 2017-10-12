package services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{JsObject, JsString, pimpAny, _}
import DefaultJsonProtocol._

trait JsonSupport extends SprayJsonSupport with CustomJsonProtocol{
  import DefaultJsonProtocol._

  implicit val accountJsonFormat = jsonFormat2(BankAccount)
  implicit val withdrawRequestFormat = jsonFormat2(Withdraw)
  implicit val transferRequestFormat = jsonFormat3(Transfer)
  implicit val depositRequestFormat = jsonFormat2(Deposit)

  implicit val insufficientFundFormat = jsonFormat1(InsufficientFund)
  implicit val accountNotFoundFormat = jsonFormat1(AccountNotFound)
  implicit val internalErrorFormat = jsonFormat1(RequestNotValid)
  implicit val amountNotValidFormat = jsonFormat1(AmountNotValid)
  implicit val successTransactionResponseFormat = jsonFormat2(SuccessTransactionResponse)
}

trait CustomJsonProtocol {
  import DefaultJsonProtocol._

  implicit object ErrorJsonFormat extends RootJsonFormat[Error] {
    def write(c: Error) = JsObject(("err_msg",c.errorMessage.toJson))

    def read(value: JsValue) = {
      value.asJsObject.getFields("err_msg") match {
        case Seq(JsString(x)) => new Error {
          override def errorMessage: String =  x
        }
      }
    }
  }

  implicit object ErrorResponseJsonFormat extends RootJsonFormat[ErrorResponse] {
    def write(c: ErrorResponse) = JsObject(("error",c.error.toJson))

    def read(value: JsValue) = {
      value.asJsObject.getFields("error") match {
        case Seq(JsObject(x)) => ErrorResponse(JsObject(x).convertTo[Error])
      }
    }
  }

  implicit object FailedTransactionResponseJsonFormat extends RootJsonFormat[FailedTransactionResponse] {
    def write(tx: FailedTransactionResponse) = {
      JsObject(
        "id" -> JsString(tx.id),
        "reason" -> tx.reason.toJson
      )
    }

    def read(value: JsValue) = {
      value.asJsObject.getFields("id","reason") match {
        case Seq(JsString(id),JsObject(reason)) => FailedTransactionResponse(id,JsObject(reason).convertTo[Error])
      }
    }
  }

//  implicit object TransactionJsonFormat extends RootJsonFormat[Transaction] {
//    def write(tx: Transaction) = tx match {
//      case t: SuccessTransaction => JsObject(
//        "id" -> JsString(t.id),
//        "request" -> t.request.toJson,
//        "balance" -> JsString(t.balance.toString())
//      )
//      case t: FailedTransaction => JsObject(
//        "id" -> JsString(t.id),
//        "request" -> t.request.toJson,
//        "error" -> t.error.toJson
//      )
//    }
//
//    def read(value: JsValue) = {
//      value.asJsObject.getFields("id","request","balance") match {
//        case Seq(JsString(id),JsObject(reason),JsString(balance)) =>
//          SuccessTransaction(id,JsObject(reason).convertTo[TransactionRequest],BigDecimal(balance))
//        case Seq(JsString(id),JsObject(reason),JsObject(error)) =>
//          FailedTransactionResponse(id,JsObject(reason).convertTo[Error])
//      }
//    }
//  }

}