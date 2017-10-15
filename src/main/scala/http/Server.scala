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
import scala.collection.concurrent.Map
import scala.concurrent.Future

class Server extends JsonSupport {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //  var db = Map.empty[String,BankAccount]
  val service = new AccountService {
    override val accountsDB = new ConcurrentHashMap[String, BankAccount]().asScala
    override val transactionsDB = new ConcurrentHashMap[String, Transaction]().asScala
    override val txDB: Map[String, BankAccount] = new ConcurrentHashMap[String, BankAccount]().asScala
  }

  val regectionHandler = RejectionHandler.newBuilder()
    //    .handleNotFound { complete((StatusCodes.NotFound, "Oh man, what you are looking for is long gone.")) }
    .handle {
    case MalformedRequestContentRejection(msg, _) => complete((StatusCodes.BadRequest, ErrorResponse(RequestNotValid().errorMessage)))
    case x => x; complete(StatusCodes.BadRequest)
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
//        path("withdraw") {
//          post {
//            entity(as[Withdraw]) { withdrawRequest =>
//              service.withdraw(withdrawRequest) match {
//                case SuccessTransaction(id, _, balance) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
//                case FailedTransaction(id, _, err: InsufficientFund) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
//                case FailedTransaction(id, _, err: AmountNotValid) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
//                case FailedTransaction(id, _, err: AccountNotFound) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
//                case _ => complete(StatusCodes.InternalServerError)
//              }
//            }
//          }
//        } ~
          path("transfer") {
            post {
              entity(as[Transfer]) { transferRequest =>
                service.concurrentTransfer(transferRequest) match {
                  case SuccessTransaction(id, _, balance) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
                  case FailedTransaction(id, _, err: AccountNotFound) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: InsufficientFund) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, err.errorMessage))
                  case FailedTransaction(id, _, err: TransactionNotApplicable.type ) => complete(StatusCodes.ServiceUnavailable, FailedTransactionResponse(id, ""))
                }
              }
            }
          } ~
//          path("deposit") {
//            post {
//              entity(as[Deposit]) { depositRequest =>
//                service.deposit(depositRequest) match {
//                  case SuccessTransaction(id, _, balance) => complete(StatusCodes.OK, SuccessTransactionResponse(id, balance))
//                  case FailedTransaction(id, _, error: AccountNotFound) => complete(StatusCodes.NotFound, FailedTransactionResponse(id, error.errorMessage))
//                  case FailedTransaction(id, _, error: AmountNotValid) => complete(StatusCodes.BadRequest, FailedTransactionResponse(id, error.errorMessage))
//                }
//              }
//            }
//          } ~
          pathPrefix("tx") {
            get {
              parameter('id.as[String]){ id =>
                service.getTransaction(id) match {
                  case Right(tx: SuccessTransaction) => complete(StatusCodes.OK, s"${tx.id}, ${tx.balance}")
                  case Right(tx: FailedTransaction) => complete(StatusCodes.OK, s"${tx.id}")
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

}