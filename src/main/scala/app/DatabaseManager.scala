package app

import java.net.URI

import scala.io.Source

/**
  * Created by Wojciech Baczyński on 26.11.17.
  */

case class DatabaseManager(database: Map[URI, Item]) {
  def loadRecords(): DatabaseManager = {
    val t0 = System.nanoTime()

    val filename = getClass.getResource("/query_result")
    var newListOfItems: Map[URI, Item] = Map.empty

    Source.fromURL(filename).getLines()
      .drop(1)
      .foreach(line => {
        var parsedLine = line.split(",").toList

        if (parsedLine.size == 2) parsedLine = parsedLine :+ ""
        else if (parsedLine.size == 1) parsedLine = parsedLine :+ "" :+ ""
        else if (parsedLine.isEmpty) parsedLine = parsedLine :+ "" :+ "" :+ ""

        val uriId = hash((parsedLine(1) + "_" + parsedLine(2)).replace("\"", "").replace(" ", "_"))
        val uri = new URI(uriId)

        if (newListOfItems.contains(uri)) {
          val oldCount = newListOfItems(uri).count
          newListOfItems = newListOfItems.-(uri)
          newListOfItems = newListOfItems.updated(uri, Item(uri, parsedLine(1), parsedLine(2), scala.util.Random.nextInt(100), oldCount + 1))
        } else
          newListOfItems = newListOfItems.updated(uri, Item(uri, parsedLine(1), parsedLine(2), scala.util.Random.nextInt(100), 1))
      })

    val t1 = System.nanoTime()

    println("\033[33mLoaded \033[33;1m" + newListOfItems.size + "\033[33m unique records\033[0m in \033[33;1m" + Math.round((t1 - t0) / 10000000) / 100.0 + "\033[0m seconds.")

    copy(database = newListOfItems)
  }

  private def hash(s: String) = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16).reverse.padTo(32, "0").reverse.mkString
  }

  def search(query: String): List[(Int, Item)] = {
    val keywords = query.split(" ").toList
    var scoreList: List[(Int, Item)] = List.empty

    database.values.foreach(item =>
      scoreList = scoreList :+ (score(keywords, item), item)
    )

    scoreList = scoreList.sortWith(_._1 > _._1)
    scoreList.take(10)
  }

  private def score(keywords: List[String], item: Item): Int = {
    var count = 0

    item.name.split(" ").foreach(word =>
      keywords.foreach(keyword =>
        if (word.toLowerCase.contains(keyword.toLowerCase))
          count = count + 1
      ))

    item.brand.split(" ").foreach(word =>
      keywords.foreach(keyword =>
        if (word.toLowerCase.contains(keyword.toLowerCase))
          count = count + 1
      ))

    count
  }

}

object DatabaseManager {
  val empty = DatabaseManager(Map.empty)
}