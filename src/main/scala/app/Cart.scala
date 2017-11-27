package app

import java.net.URI

/**
  * Created by Wojciech Baczyński on 11.11.17.
  */

/**
  * @param id : unique item identifier (java.net.URI)
  */
case class Item(id: URI, name: String, brand: String, price: BigDecimal, count: Int)

case class Cart(items: Map[URI, Item]) {
  def addItem(newItem: Item): Cart = {
    val currentCount = if (items contains newItem.id) items(newItem.id).count else 0
    copy(items.updated(newItem.id, newItem.copy(count = newItem.count + currentCount)))
  }

  @throws(classOf[ActionCouldNotBeInvokedException])
  def removeItem(oldItem: Item, count: Int): Cart = {
    if (items.contains(oldItem.id)) {
      if (items(oldItem.id).count - count <= 0)
        copy(items.filterKeys(_ != oldItem.id))
      else
        copy(items.updated(oldItem.id, oldItem.copy(count = oldItem.count - count)))
    } else
      throw ActionCouldNotBeInvokedException("Tried to remove non existing element")
  }

  def clearCart(): Cart = {
    copy(Map.empty)
  }
}

object Cart {
  val empty = Cart(Map.empty)
}

case class ActionCouldNotBeInvokedException(message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)