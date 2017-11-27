package app

import akka.actor.{ActorSystem, Props}
import app.Common.Init
import com.typesafe.config.ConfigFactory

import scala.util.control.Breaks.{break, breakable}

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object DatabaseApp extends App {
  val config = ConfigFactory.load()
  val databaseSystem = ActorSystem("database", config.getConfig("database").withFallback(config))

  val productCatalogActor = databaseSystem.actorOf(Props(new ProductCatalog(new DatabaseManager(Map.empty))), "productCatalogActor")

  productCatalogActor ! Init

  println("+ -----\033[1;33m DATABASE \033[0m----- +")
  println("|\033[33m q / quit / exit / :q \033[0m|")
  println("+ -------------------- +")

  println("\033[33mDatabase working...\033[0m")

  breakable {
    while (true) {
      val cmd = scala.io.StdIn.readLine()
      if (List("q", "quit", "exit", ":q").contains(cmd))
        break
    }
  }

  databaseSystem.terminate()
}
