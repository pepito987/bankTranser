package http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.stream.ActorMaterializer
import services._
import spray.json.DefaultJsonProtocol._
import spray.json.{pimpAny, _}

import scala.collection.mutable

class Server extends JsonSupport {

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //  var db = Map.empty[String,BankAccount]
  val service = new AccountService {
    override val db: mutable.Map[String, BankAccount] = mutable.Map.empty
  }

  val route = respondWithHeader(RawHeader("Content-Type","application/json")){
    pathPrefix("account") {
      get {
        path(IntNumber) { id =>
          service.get(s"$id") match {
            case Some(x) => complete(StatusCodes.OK,x)
            case _ => complete(StatusCodes.NotFound)
          }
        } ~
          pathEnd {
            complete(StatusCodes.NotImplemented)
          }
      } ~
        post {
          service.create() match {
            case Some(x) => complete(StatusCodes.Created, x)
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
    } ~
      pathPrefix("withdraw") {
        post {
          entity(as[String]) { body =>
            val withdrawRequest = body.parseJson.convertTo[WithdrawRequest]
            service.withdraw(withdrawRequest) match {
              case Right(acc) => complete(StatusCodes.OK, acc)
              case Left(err) => complete(StatusCodes.InternalServerError, err)
            }
          }
        }
      } ~
      pathPrefix("transfer") {
        post {
          entity(as[String]) {
            case b if b.isEmpty => complete(StatusCodes.BadRequest)
            case body => {
              val transferRequest = body.parseJson.convertTo[TransferRequest]
              service.transfer(transferRequest) match {
                case Right(_) => complete(StatusCodes.OK)
                case Left(_) => complete(StatusCodes.BadRequest)
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