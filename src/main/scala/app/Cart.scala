package app

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.event.Logging
import app.Common._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Cart {
  def props(checkoutActor: ActorRef): Props = Props(new Cart(checkoutActor))

  // Messages
  final case class ItemAdded(newItem: String)
  final case class ItemRemoved(oldItem: String)

  // Messages for Timers
  private case object CartTimerID
  private case object CartTimerExpired
}

class Cart(checkoutActor: ActorRef) extends Actor with Timers {

  import Cart._

  private var listOfItems = List[String]()
  val log = Logging(context.system, this)

  def setTimer(): Unit = {
    if (timers.isTimerActive(CartTimerID))
      timers.cancel(CartTimerID)
    timers.startSingleTimer(CartTimerID, CartTimerExpired, expirationTime)
  }

  def unsetTimer(): Unit = {
    if (timers.isTimerActive(CartTimerID))
      timers.cancel(CartTimerID)
  }

  def receive: Receive = empty

  def empty: Receive = {
    case Cart.ItemAdded(newItem) =>
      println("(0)")
      listOfItems = listOfItems :+ newItem
      become_(context, nonEmpty, "NonEmpty")
      println("(" + listOfItems.size + ") " + listOfItems)
      setTimer()

    case _ => println("[WARN | Bad request] (Cart / empty)")
  }

  def nonEmpty: Receive = {
    case Cart.CartTimerExpired =>
      listOfItems = List[String]()
      become_(context, empty, "Empty")
      println("  /CartTimerExpired/")

    case Cart.ItemAdded(newItem) =>
      listOfItems = listOfItems :+ newItem
      println("(" + listOfItems.size + ") " + listOfItems)
      setTimer()

    case Cart.ItemRemoved(oldItem) =>
      if (!listOfItems.contains(oldItem))
        println("[WARN | Tried to remove not existing element]")
      else {
        listOfItems = listOfItems.filter(_ != oldItem)
        println("(" + listOfItems.size + ") " + listOfItems)
        if (listOfItems.isEmpty)
          become_(context, empty, "Empty")
      }
      setTimer()

    case Common.CheckoutStarted =>
      println("Checkout started")
      become_(context, inCheckout, "InCheckout")
      unsetTimer()
      checkoutActor ! Common.CheckoutStarted(listOfItems.size, self)
    case _ => println("[WARN | Bad request] (Cart / nonEmpty)")
  }

  def inCheckout: Receive = {
    case Common.CheckoutCancelled =>
      log.debug("-- Checkout cancelled")
      become_(context, nonEmpty, "NonEmpty")
      setTimer()

    case Common.CheckoutClosed =>
      println("-- Checkout closed")
      listOfItems = List[String]()
      become_(context, empty, "Empty")

    case Common.CartIsEmpty =>
      println("-- Something went wrong ! Received info that Cart is empty !")

    case _ => println("[WARN | Bad request] (Cart / inCheckout)")
  }
}
