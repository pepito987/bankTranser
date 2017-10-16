package http

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import services._

import scala.collection.JavaConverters._

class Server extends JsonSupport {

  implicit val system = ActorSystem("bank-transfer-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //  var db = Map.empty[String,BankAccount]
  val service = new AccountService {
    override val accountsDB = new ConcurrentHashMap[String, BankAccount]().asScala
    override val transactionsDB = new ConcurrentHashMap[String, TransactionStatus]().asScala
  }

  val regectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MalformedRequestContentRejection(msg, _) => complete((StatusCodes.BadRequest, ErrorResponse(RequestNotValid().errorMessage)))
      case x => complete(StatusCodes.BadRequest)
    }
    .result()

  val route = handleRejections(regectionHandler) {
    pathPrefix("account") {
      pathEndOrSingleSlash {
        post {
          complete(StatusCodes.Created, service.create())
        }
      } ~
        pathPrefix(".*".r) { accId =>
          pathEndOrSingleSlash {
            get {
              service.getAccount(accId) match {
                case Some(x) => complete(StatusCodes.OK, x)
                case None => complete(StatusCodes.NotFound, ErrorResponse(AccountNotFound().errorMessage))
              }
            }
          }
        } ~
        path(".*".r / "withdraw") { srcAccId =>
          pathEndOrSingleSlash {
            post {
              entity(as[SingleTransaction]) { request =>
                service.withdraw(Withdraw(srcAccId, request.amount)) match {
                  case SuccessTransaction(id, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                  case _ => complete(StatusCodes.InternalServerError)
                }
              }
            }
          }
        } ~
        path(".*".r / "deposit") { srcAccId =>
          pathEndOrSingleSlash{
            post {
              entity(as[SingleTransaction]) { request =>
                service.deposit(Deposit(srcAccId,request.amount)) match {
                  case SuccessTransaction(id, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, error: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, error.errorMessage))
                  case FailedTransaction(id, _, error: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, error.errorMessage))
                }
              }
            }
          }
        } ~
        path(".*".r / "transfer") { srcAccId =>
          pathEndOrSingleSlash{
            post {
              entity(as[Transaction]) { request =>
                service.transfer(Transfer(srcAccId,request.to,request.amount)) match {
                  case SuccessTransaction(id, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                }
              }
            }
          }

        }~
        pathPrefix(".*".r / "tx"){ accountId =>
          path(".*".r) { transactionId =>
            pathEndOrSingleSlash {
              get {
                service.getTransaction(transactionId,accountId) match {
                  case Right(tx: SuccessTransaction) => complete(StatusCodes.OK, FetchTransactionResponse(tx.id, accountId, Some(tx.balance), tx.time))
                  case Right(tx: FailedTransaction) => complete(StatusCodes.OK, FetchTransactionResponse(tx.id, accountId, time = tx.time))
                  case Left(x) => complete(StatusCodes.NotFound, ErrorResponse(x.errorMessage))
                }
              }
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
  new Server().start
}