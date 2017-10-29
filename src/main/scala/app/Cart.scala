package app

import akka.actor.{Actor, ActorRef, Props, Timers}
import app.Common._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Cart {

  // Messages
  final case class ItemAdd(newItem: String)
  final case class ItemRemove(oldItem: String)
  final case object CartIsEmpty

  final case object CheckoutStart
  final case class CheckoutStarted(actor: ActorRef, itemsCount: Int)

  final case object CheckoutCancel
  final case object CheckoutCancelled
  final case object CheckoutClose
  final case object CheckoutClosed

  // Messages for Timers
  private case object CartTimerID
  private case object CartTimerExpired

}

class Cart(customerActor: ActorRef) extends Actor with Timers {

  import Cart._

  private var checkoutActor: Option[ActorRef] = None
  private var listOfItems = List[String]()

  def setCartTimer(): Unit = {
    if (timers.isTimerActive(CartTimerID))
      timers.cancel(CartTimerID)
    timers.startSingleTimer(CartTimerID, CartTimerExpired, expirationTime)
  }

  def unsetCartTimer(): Unit = {
    if (timers.isTimerActive(CartTimerID))
      timers.cancel(CartTimerID)
  }

  def receive: Receive = empty

  def empty: Receive = {
    case ItemAdd(newItem) =>
      listOfItems = listOfItems :+ newItem
      println("(" + listOfItems.size + ") " + listOfItems)
      become_(context, nonEmpty, "Empty", "NonEmpty")
      setCartTimer()

    case _ => printWarn("Bad request", "Cart / empty")
  }

  def nonEmpty: Receive = {
    case CartTimerExpired =>
      listOfItems = List[String]()
      printTimerExpired("CartTimer")
      become_(context, empty, "NonEmpty", "Empty")
      customerActor ! CartIsEmpty

    case ItemAdd(newItem) =>
      listOfItems = listOfItems :+ newItem
      println("(" + listOfItems.size + ") " + listOfItems)
      setCartTimer()

    case ItemRemove(oldItem) =>
      if (!listOfItems.contains(oldItem))
        customerActor ! ActionCouldNotBeInvoked("Tried to remove non existing element")
      else {
        listOfItems = listOfItems.filter(_ != oldItem)
        println("(" + listOfItems.size + ") " + listOfItems)
        if (listOfItems.isEmpty) {
          become_(context, empty, "NonEmpty", "Empty")
          customerActor ! CartIsEmpty
        }
      }
      setCartTimer()

    case CheckoutStart =>
      unsetCartTimer()
      become_(context, inCheckout, "NonEmpty", "InCheckout")
      if (checkoutActor.isEmpty)
        checkoutActor = Option(context.actorOf(
          Props(new Checkout(customerActor)), "checkoutActor"))
      checkoutActor.get ! CheckoutStarted(self, listOfItems.size)
      customerActor ! CheckoutStarted(checkoutActor.get, listOfItems.size)

    case _ => printWarn("Bad request", "Cart / nonEmpty")
  }

  def inCheckout: Receive = {
    case CheckoutCancel =>
      customerActor ! CheckoutCancelled
      become_(context, nonEmpty, "InCheckout", "NonEmpty")
      setCartTimer()

    case CheckoutClose =>
      listOfItems = List[String]()
      customerActor ! CheckoutClosed
      become_(context, empty, "InCheckout", "Empty")
      customerActor ! CartIsEmpty

    case CartIsEmpty =>
      printWarn("Something went wrong !", "Cart is empty in bad checkout !")

    case _ => printWarn("Bad request", "Cart / inCheckout")
  }
}
