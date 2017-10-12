package services

import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import spray.json.{pimpAny, pimpString}

class JsonSupportSpec extends WordSpec with Matchers with BeforeAndAfter with JsonSupport{


  "toJson" should {
    "convert Error to json " in {
      val x = new Error {
        override def errorMessage: String = "Error message"
      }

      x.toJson.toString() shouldBe """{"err_msg":"Error message"}"""

    }

    "convert a String into an Error" in {
      val str = """{"err_msg":"Error message"}"""
      val err = new Error {
        override def errorMessage: String = "Error message"
      }

      str.parseJson.convertTo[Error].errorMessage shouldBe err.errorMessage
    }

    "convert an ErrorResponse in json" in {
      val response = ErrorResponse(AccountNotFound()).toJson.toString()

      response shouldBe """{"error":{"err_msg":"Account not found"}}"""

    }

    "convert a String in ErrorResponse" in {
      val str = """{"error":{"err_msg":"Account not found"}}"""

      val response = ErrorResponse(AccountNotFound())

      println(str.parseJson.convertTo[ErrorResponse].error.errorMessage)
      //      shouldBe response.error.err_msg

    }


  }

}
