package app

import java.net.URI

import akka.actor.{Actor, ActorRef, Props, Timers}
import app.Common._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object CartManager {

  // Messages
  final case class ItemAdd(newItem: Item)
  final case class ItemRemove(oldItem: Item, count: Integer)
  final case object CartIsEmpty

  final case object CheckoutStart
  final case class CheckoutStarted(actor: ActorRef, itemsCount: Int)

  final case object CheckoutCancel
  final case object CheckoutCancelled
  final case object CheckoutClose
  final case object CheckoutClosed

  // Messages for Timers
  final case object CartTimerID extends TimerID
  final case object CartTimerExpired extends TimerMsg

}

class CartManager(customerActor: ActorRef, var shoppingCart: Cart) extends Actor with Timers {

  import CartManager._

  def this(customerActor: ActorRef) = this(customerActor, Cart.empty)

  private var checkoutActor: Option[ActorRef] = None

  def getListOfItems: Map[URI, Item] = shoppingCart.items

  def unsetCartTimer(): Unit = unsetTimer(timers, CartTimerID)
  def setCartTimer(): Unit = setTimer(timers, CartTimerID, CartTimerExpired)

  def receive: Receive = empty

  def empty: Receive = {
    case ItemAdd(newItem) =>
      shoppingCart = shoppingCart.addItem(newItem)
      println("(" + getListOfItems.size + ") " + getListOfItems)
      become_(context, nonEmpty, "Empty", "NonEmpty")
      setCartTimer()

    case _ => printWarn("Bad request", "CartManager / empty")
  }

  def nonEmpty: Receive = {
    case CartTimerExpired =>
      shoppingCart = shoppingCart.clearCart()
      printTimerExpired("CartTimer")
      become_(context, empty, "NonEmpty", "Empty")
      customerActor ! CartIsEmpty

    case ItemAdd(newItem) =>
      shoppingCart = shoppingCart.addItem(newItem)
      println("(" + getListOfItems.size + ") " + getListOfItems)
      setCartTimer()

    case ItemRemove(oldItem, count) =>
      try {
        shoppingCart = shoppingCart.removeItem(oldItem, count)
      } catch {
        case e: ActionCouldNotBeInvokedException =>
          customerActor ! ActionCouldNotBeInvoked(e.message)
      }

      println("(" + getListOfItems.size + ") " + getListOfItems)
      if (getListOfItems.isEmpty) {
        become_(context, empty, "NonEmpty", "Empty")
        customerActor ! CartIsEmpty
      }
      setCartTimer()

    case CheckoutStart =>
      unsetCartTimer()
      become_(context, inCheckout, "NonEmpty", "InCheckout")
      if (checkoutActor.isEmpty)
        checkoutActor = Option(context.actorOf(
          Props(new Checkout(customerActor)), "checkoutActor"))
      checkoutActor.get ! CheckoutStarted(self, getListOfItems.size)
      customerActor ! CheckoutStarted(checkoutActor.get, getListOfItems.size)

    case _ => printWarn("Bad request", "CartManager / nonEmpty")
  }

  def inCheckout: Receive = {
    case CheckoutCancel =>
      customerActor ! CheckoutCancelled
      become_(context, nonEmpty, "InCheckout", "NonEmpty")
      setCartTimer()

    case CheckoutClose =>
      shoppingCart.clearCart()
      customerActor ! CheckoutClosed
      become_(context, empty, "InCheckout", "Empty")
      customerActor ! CartIsEmpty

    case CartIsEmpty =>
      printWarn("Something went wrong !", "CartManager is empty in bad checkout !")

    case _ => printWarn("Bad request", "CartManager / inCheckout")
  }
}
