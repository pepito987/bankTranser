package http

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import services._

import collection.JavaConverters._
import scala.collection.concurrent.Map

class Server extends JsonSupport {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //  var db = Map.empty[String,BankAccount]
  val service = new AccountService {
    override val accountsDB = new ConcurrentHashMap[String,BankAccount]().asScala
    override val transactionsDB = new ConcurrentHashMap[String,Transaction]().asScala
  }

  val regectionHandler = RejectionHandler.newBuilder()
    //    .handleNotFound { complete((StatusCodes.NotFound, "Oh man, what you are looking for is long gone.")) }
    .handle { case MalformedRequestContentRejection(msg, _) => complete((StatusCodes.BadRequest, ErrorResponse(RequestNotValid()))) }
    .result()

  val route = handleRejections(regectionHandler) {
    pathPrefix("account") {
      get {
        path(IntNumber) { id =>
          service.get(s"$id") match {
            case Right(x) => complete(StatusCodes.OK, x)
            case Left(y) => complete(StatusCodes.NotFound, ErrorResponse(y))
          }
        } ~
          pathEnd {
            complete(StatusCodes.NotImplemented, RequestNotValid())
          }
      } ~
        post {
         complete(StatusCodes.Created,  service.create())
        }
    } ~
      pathPrefix("withdraw") {
        post {
          entity(as[Withdraw]) { withdrawRequest =>
            service.withdraw(withdrawRequest) match {
              case Right(tx) => complete(StatusCodes.OK, SuccessTransactionResponse(tx.id, tx.balance))
              case Left(err:InsufficientFund) => complete(StatusCodes.BadRequest, ErrorResponse(err))
              case Left(err:AmountNotValid) => complete(StatusCodes.BadRequest, ErrorResponse(err))
              case Left(err:AccountNotFound) => complete(StatusCodes.NotFound, ErrorResponse(err))
              case Left(err) => complete(StatusCodes.InternalServerError, ErrorResponse(err))
            }
          }
        }
      } ~
      pathPrefix("transfer") {
        post {
          entity(as[Transfer]) { transferRequest =>
            service.transfer(transferRequest) match {
              case Right(tx) => complete(StatusCodes.OK, SuccessTransactionResponse(tx.id,tx.balance))
              case Left(err:AccountNotFound) => complete(StatusCodes.NotFound, ErrorResponse(err))
              case Left(err:InsufficientFund) => complete(StatusCodes.BadRequest, ErrorResponse(err))
            }
          }
        }
      } ~
    pathPrefix("deposit") {
      post {
        entity(as[Deposit]) { depositRequest =>
          service.deposit(depositRequest) match {
            case Right(tx) => complete(StatusCodes.OK, SuccessTransactionResponse(tx.id,tx.balance))
            case Left(error:AccountNotFound) => complete(StatusCodes.NotFound,ErrorResponse(error))
            case Left(error:AmountNotValid) => complete(StatusCodes.BadRequest,ErrorResponse(error))
          }
        }
      }
    }
  }


  def start = {
    Http().bindAndHandle(route, "localhost", 8080)
  }

  def stop = {
    system.terminate()
  }

}

object Server extends App {

}