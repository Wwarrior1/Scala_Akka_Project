package app

import akka.actor.Actor
import app.Common.Init

/**
  * Created by Wojciech Baczyński on 26.11.17.
  */

object ProductCatalog {
  final case class SearchItem(query: String)
  final case class ItemsFound(items: List[(Int, Item)])
  final case object ItemNotFound
}

class ProductCatalog(var databaseManager: DatabaseManager) extends Actor {

  import ProductCatalog._

  override def receive: Receive = {
    case Init => databaseManager = databaseManager.loadRecords()

    case SearchItem(query) =>
      val results = databaseManager.search(query)
      sender ! ItemsFound(results)

    //    case ItemNotFound => {
    //    }
  }

}
