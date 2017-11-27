package app

import akka.actor.{ActorSystem, Props}
import app.Common.Init
import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object MainApp extends App {
  val config: Config = ConfigFactory.load()
  val clientSystem = ActorSystem("client", config.getConfig("client").withFallback(config))

  val clientActor = clientSystem.actorOf(Props[Client], "clientActor")

  clientActor ! Init
}
