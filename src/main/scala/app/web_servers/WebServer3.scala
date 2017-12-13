package app.web_servers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

/**
  * Created by Wojciech Baczyński on 13.12.17.
  */
object WebServer3 extends App {
  val config = ConfigFactory.load()
  implicit val system: ActorSystem = ActorSystem("webserver3", config.getConfig("webserver3").withFallback(config))

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val route =
    path("payment_request") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>PayPal accepted !</h1>"))
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8083)

  println(s"\033[32;1mPayPal\033[0m Server online at http://localhost:8083/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
