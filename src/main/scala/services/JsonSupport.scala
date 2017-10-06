package services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{pimpAny, _}

trait JsonSupport extends SprayJsonSupport with CustomJsonProtocol{
  import DefaultJsonProtocol._

  implicit val accountJsonFormat = jsonFormat2(BankAccount)
  implicit val withdrawRequestFormat = jsonFormat2(WithdrawRequest)
  implicit val transferRequestFormat = jsonFormat3(TransferRequest)
  implicit val insufficientFundFormat = jsonFormat1(InsufficientFund)
  implicit val accountNotFoundFormat = jsonFormat1(AccountNotFound)
  implicit val internalErrorFormat = jsonFormat1(InternalError)
}

trait CustomJsonProtocol {
  import DefaultJsonProtocol._
  implicit object ErrorJsonFormat extends RootJsonFormat[Error] {
    def write(c: Error) = JsObject(("err_msg",c.err_msg.toJson))

    def read(value: JsValue) = {
      value.asJsObject.getFields("err_msg") match {
        case Seq(JsString(x)) => new Error {
          override def err_msg: String =  x
        }
      }
    }
  }

  implicit object ErrorResponseJsonFormat extends RootJsonFormat[ErrorResponse] {
    def write(c: ErrorResponse) = JsObject(("error",c.error.toJson))

    def read(value: JsValue) = {
      value.asJsObject.getFields("error") match {
        case Seq(JsObject(x)) => new ErrorResponse(JsObject(x).convertTo[Error])
      }
    }
  }

}