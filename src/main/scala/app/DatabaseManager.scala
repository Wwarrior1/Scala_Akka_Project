package app

import java.net.URI

import scala.collection.immutable.{HashMap, Map}
import scala.io.Source

/**
  * Created by Wojciech Baczyński on 26.11.17.
  */

object DatabaseManager {
  val empty = DatabaseManager(HashMap.empty, Map.empty)

  final case object LoadDatabase
  final case object IndexDatabase
}

case class DatabaseManager(database: HashMap[URI, Item], index: Map[String, List[URI]]) {
  def loadRecords(): DatabaseManager = {
    val filename = getClass.getResource("/query_result_micro")
    var newListOfItems: HashMap[URI, Item] = HashMap.empty

    val totalLines = scala.io.Source.fromURL(filename).getLines().size
    var currentLine = 0.0
    var percentageStep = 0

    println("\n>> \033[34;1mLOADING RECORDS\033[0m <<")
    print(" ----------\n ")

    val t0 = System.nanoTime()

    Source.fromURL(filename).getLines()
      .drop(1)
      .foreach(line => {

        currentLine += 1
        if ((currentLine / totalLines * 100) > percentageStep) {
          percentageStep += 10
          if (percentageStep <= 90) print('.')
          else println(".")
        }

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

    println("\033[33mLoaded \033[33;1m" + totalLines + "\033[33;0m (\033[33;1m" + newListOfItems.size + "\033[33m unique) records\033[0m in \033[33;1m" + Math.round((t1 - t0) / 10000000) / 100.0 + "\033[0m seconds.")

    copy(database = newListOfItems)
  }

  def indexDatabase(): DatabaseManager = {
    println("\n>> \033[34;1mINDEXING\033[0m <<")
    print(" ----------\n ")

    var indexMap: collection.mutable.MutableList[(String, URI)] = collection.mutable.MutableList.empty

    val totalRecords = database.values.size
    var currentLine = 0.0
    var percentageStep = 0

    val t0 = System.nanoTime()

    database.values.foreach(item => {

      currentLine += 1
      if ((currentLine / totalRecords * 100) > percentageStep) {
        percentageStep += 10
        if (percentageStep <= 90) print('.')
        else println(".")
      }

      (item.name + " " + item.brand)
        .toLowerCase.replace("\"", "").split(" ")
        .foreach(word => indexMap.+=((word, item.id)))

    })

    val ret = indexMap.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).toList }

    val t1 = System.nanoTime()

    println("\033[33mIndexed in \033[33;1m" + Math.round((t1 - t0) / 10000000) / 100.0 + "\033[0m seconds.")

    //    println("+++++\n")
    //    ret.foreach(e =>
    //      println(e._1 + " -> " + e._2))
    //    println("\n+++++")

    copy(index = ret)
  }

  private def hash(s: String) = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16).reverse.padTo(32, "0").reverse.mkString
  }

  def search(query: String): (List[(Int, Item)], Float) = {
    val t0 = System.nanoTime()

    val keywords = query.split(" ").toList
    var scoreList: List[(Int, Item)] = List.empty

    var uriList: List[URI] = List.empty

    keywords.foreach(keyword => {
      val uri = index.getOrElse(keyword.toLowerCase, null)
      if (uri != null) uriList = uriList.++(uri)
    })

    scoreList = uriList.groupBy(identity).mapValues(_.size).toVector.map(_.swap).toList.sortWith(_._1 > _._1)
      .map(item => (item._1, database(item._2)))

    val t1 = System.nanoTime()

    (scoreList.take(10), Math.round((t1 - t0) / 10000000) / 100.0.toFloat)
  }

}