package services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.joda.time.DateTime
import spray.json._

trait JsonSupport extends SprayJsonSupport {
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
  implicit val FailedTransactionResponseFormat = jsonFormat2(FailedTransactionResponse)
  implicit val ErrorResponseFormat = jsonFormat1(ErrorResponse)

  implicit object FetchTransactionResponseFormat extends RootJsonFormat[FetchTransactionResponse] {
    def write(response: FetchTransactionResponse) = {
      JsObject(
        "id" -> JsString(response.id),
        "balance" -> response.balance.toJson,
        "time" -> JsString(response.toString)
      )
    }

    def read(value: JsValue) = {
      value.asJsObject.getFields("id","balance","time") match {
        case Seq(JsString(id),JsObject(balance),JsString(time)) =>
          FetchTransactionResponse(id,Some(JsObject(balance).convertTo[BigDecimal]),DateTime.parse(time))
      }
    }
  }

}
