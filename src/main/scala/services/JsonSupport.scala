package services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{JsObject, pimpAny, _}
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

  implicit object ResponseJsonFormat extends RootJsonFormat[Response] {
    def write(c: Response) = c.error.map{ error =>
      JsObject(("error",error.toJson))
    }.getOrElse(JsObject())

    def read(value: JsValue) = {
      value.asJsObject.getFields("error") match {
        case Seq(JsObject(x)) => {
          Response(Some(JsObject(x).convertTo[Error]))
        }
      }
    }
  }

}