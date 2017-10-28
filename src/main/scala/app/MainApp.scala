package app

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object MainApp extends App {
  val system = ActorSystem("project")
  val clientActor = system.actorOf(Props[Client], "clientActor")

  clientActor ! Client.Init

  Await.result(system.whenTerminated, Duration.Inf)

}
