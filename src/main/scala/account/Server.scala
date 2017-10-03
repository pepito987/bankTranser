package account

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import spray.json.pimpAny

import scala.util.Random
//import akka.http.scaladsl.server.directives.MethodDirectives._
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future

class Server extends JsonSupport with AccountOperations with Db{

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  var db = Map.empty[String,BankAccount]

  val route = {
    pathPrefix("accounts"){
      get{
        complete(db.keySet.toJson)
      }
    } ~
    pathPrefix("account"){
      get {
        path(IntNumber) { id =>
          val response = db.get(s"$id").map(x =>
            HttpResponse(entity = x.toJson.toString())
          ).getOrElse(HttpResponse(StatusCodes.NotFound))
          complete(response)
        } ~
        pathEnd {
          complete(StatusCodes.NotImplemented)
        }
      } ~
      post {
        entity(as[String]) { body =>
          val bl = body.parseJson.convertTo[BigDecimal]
          val id = Random.nextInt().toString
          db = db.updated(id, BankAccount(id,bl))
          complete(HttpResponse(status = StatusCodes.Created, entity = id.toJson.toString()))
        }
      }
    } ~
    pathPrefix("withdraw") {
      post {
        entity(as[String]) { body =>
          val withdrawRequest = body.parseJson.convertTo[WithdrawRequest]
          val amount = withdrawRequest.amount
          val id = withdrawRequest.from

          val acc = db(id)
          acc withdraw amount
          complete(HttpResponse(status = StatusCodes.OK, entity = db(id).toJson.toString()))
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