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
    override val transactionsDB = new ConcurrentHashMap[String, Transaction]().asScala
  }

  val regectionHandler = RejectionHandler.newBuilder()
    .handle {
    case MalformedRequestContentRejection(msg, _) => complete((StatusCodes.BadRequest, ErrorResponse(RequestNotValid().errorMessage)))
    case x => complete(StatusCodes.BadRequest)
  }
    .result()

  val route = handleRejections(regectionHandler) {
    pathPrefix("account") {
      get {
        parameter('id.as[String]){ id =>
          service.getAccount(s"$id") match {
            case Right(x) => complete(StatusCodes.OK, x)
            case Left(y) => complete(StatusCodes.NotFound, ErrorResponse(y.errorMessage))
          }
        }
      } ~
        post {
          complete(StatusCodes.Created, service.create())
        }
    }~
      pathPrefix("transaction") {
        path("withdraw") {
          post {
            entity(as[Withdraw]) { withdrawRequest =>
              service.withdraw(withdrawRequest) match {
                case SuccessTransaction(id, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                case FailedTransaction(id, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                case FailedTransaction(id, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                case FailedTransaction(id, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        } ~
          path("transfer") {
            post {
              entity(as[Transfer]) { transferRequest =>
                service.transfer(transferRequest) match {
                  case SuccessTransaction(id, _, balance, _) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, err: AccountNotFound, _) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: InsufficientFund, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: AmountNotValid, _) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                }
              }
            }
          } ~
          path("deposit") {
            post {
              entity(as[Deposit]) { depositRequest =>
                service.deposit(depositRequest) match {
                  case SuccessTransaction(id, _, balance,_) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, error: AccountNotFound,_) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, error.errorMessage))
                  case FailedTransaction(id, _, error: AmountNotValid,_) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, error.errorMessage))
                }
              }
            }
          } ~
          pathPrefix("tx") {
            get {
              parameter('id.as[String]){ id =>
                service.getTransaction(id) match {
                  case Right(tx: SuccessTransaction) => complete(StatusCodes.OK, FetchTransactionResponse(tx.id, Some(tx.balance), tx.time))
                  case Right(tx: FailedTransaction) => complete(StatusCodes.OK,  FetchTransactionResponse(tx.id, time = tx.time))
                  case Left(x) => complete(StatusCodes.NotFound, ErrorResponse(x.errorMessage))
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