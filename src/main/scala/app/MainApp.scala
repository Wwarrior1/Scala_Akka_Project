package app

import akka.actor.{ActorSystem, Props}
import app.Client.Terminate
import app.Common.Init

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object MainApp extends App {
  val system = ActorSystem("project")
  val clientActor = system.actorOf(Props[Client], "clientActor")

  clientActor ! Init

  system.scheduler.scheduleOnce(
    30.second, clientActor, Terminate
  )
}
