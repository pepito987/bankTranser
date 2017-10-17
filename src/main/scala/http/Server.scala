package http

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import services._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConverters._

class Server extends JsonSupport {

  implicit val system = ActorSystem("bank-transfer-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //  var db = Map.empty[String,BankAccount]
  val service = new AccountService {
    override val accountsDB = new ConcurrentHashMap[String, BankAccount]().asScala
    override val transactionsDB = new ConcurrentHashMap[String, TransactionRecord]().asScala
  }

  val regectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MalformedRequestContentRejection(msg, _) => complete((StatusCodes.BadRequest, ErrorResponse(RequestNotValid().errorMessage)))
      case _ => complete(StatusCodes.InternalServerError)
    }
    .result()

  val route = handleRejections(regectionHandler) {
    pathPrefix("account") {
      pathEndOrSingleSlash {
        post {
          entity(as[CreateAccountRequest]) { request =>
            service.create(request.userName, request.initialBalance) match {
              case Right(account) => complete(StatusCodes.Created, account)
              case Left(e:InvalidName) => complete(StatusCodes.BadRequest, ErrorResponse(e.errorMessage) )
              case Left(e:AmountNotValid) => complete(StatusCodes.BadRequest, ErrorResponse(e.errorMessage) )
            }
          }
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
                  case SuccessTransaction(id, _, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
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
                  case SuccessTransaction(id, _, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, _, error: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, error.errorMessage))
                  case FailedTransaction(id, _, _, error: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, error.errorMessage))
                }
              }
            }
          }
        } ~
        path(".*".r / "transfer") { srcAccId =>
          pathEndOrSingleSlash{
            post {
              entity(as[BiTransaction]) { request =>
                service.transfer(Transfer(srcAccId,request.to,request.amount)) match {
                  case SuccessTransaction(id, _, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
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
        } ~
        pathPrefix(".*".r / "txs"){ accountId =>
          pathEndOrSingleSlash {
            get {
              service.getTransactions(accountId) match {
                case Right(x) => complete(StatusCodes.OK, x.toJson)
                case Left(e:AccountNotFound) => complete(StatusCodes.NotFound, ErrorResponse(e.errorMessage))
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