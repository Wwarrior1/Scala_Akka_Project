package app

import akka.actor.Actor
import app.Common.Init

/**
  * Created by Wojciech Baczyński on 26.11.17.
  */

object ProductCatalog {
  final case class SearchItem(query: String)
  /** @param items (List[score: Int, item: Item], time: Float) */
  final case class ItemsFound(items: List[(Int, Item)], time: Float)
  final case object ItemNotFound
}

class ProductCatalog(var databaseManager: DatabaseManager) extends Actor {

  import ProductCatalog._

  override def receive: Receive = {
    case Init => databaseManager = databaseManager.loadRecords()

    case SearchItem(query) =>
      val (results, time) = databaseManager.search(query)
      sender ! ItemsFound(results, time)

    //    case ItemNotFound => {
    //    }
  }

}
