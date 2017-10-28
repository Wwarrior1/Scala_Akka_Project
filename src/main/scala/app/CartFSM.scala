package app

import akka.actor.{ActorRef, FSM, Props}
import app.Common._

/**
  * Created by Wojciech Baczyński on 22.10.17.
  */

object CartFSM {
  def props(checkoutActor: ActorRef): Props = Props(new CartFSM(checkoutActor))

  // States
  private case object Empty extends State
  private case object NonEmpty extends State
  case object InCheckout extends State

  // Data
  private case object Uninitialized extends Data
  private case class CartData(listOfItems: List[String]) extends Data

  // Messages
  case class ItemAdded(newItem: String)
  case class ItemRemoved(oldItem: String)
  private case object CartTimerExpired
  case object Init
}

class CartFSM(checkoutActor: ActorRef) extends FSM[State, Data] {

  import CartFSM._

  startWith(Empty, Uninitialized)

  when(Empty) {
    case Event(Init, Uninitialized) =>
      println("\n- - - - -\n")
      goto(Empty) using CartData(listOfItems = List[String]())
    case Event(ItemAdded(newItem), CartData(listOfItems)) =>
      println("(0)")
      val newList = listOfItems :+ newItem
      println("(" + newList.size + ") " + newList)
      goto(NonEmpty) using CartData(newList)
  }

  when(NonEmpty) {
    case Event(ItemAdded(newItem), CartData(listOfItems)) =>
      val newList = listOfItems :+ newItem
      println("(" + newList.size + ") " + newList)
      stay using CartData(newList)

    case Event(ItemRemoved(oldItem), CartData(listOfItems)) =>
      if (!listOfItems.contains(oldItem)) {
        println("[WARN | Tried to remove not existing element]")
        stay
      } else {
        val newList = listOfItems.filter(_ != oldItem)
        println("(" + newList.size + ") " + newList)
        if (newList.nonEmpty)
          stay using CartData(newList)
        else
          goto(Empty) using CartData(newList)
      }

    case Event(Common.CheckoutStarted, CartData(listOfItems)) =>
      println("Checkout started")
      goto(InCheckout)
  }

  when(InCheckout) {
    case Event(Common.CheckoutCancelled, _) =>
      println("-- Checkout cancelled")
      goto(NonEmpty)

    case Event(Common.CheckoutClosed, _) =>
      println("-- Checkout closed")
      goto(Empty) using CartData(listOfItems = List[String]())
  }

  onTransition {
    case Empty -> NonEmpty =>
      printMsg("Empty", "NonEmpty")
      setTimer(cartTimerName, CartTimerExpired, expirationTime)

    case NonEmpty -> Empty =>
      printMsg("NonEmpty", "Empty")

    case NonEmpty -> InCheckout =>
      cancelTimer(cartTimerName)
      printMsg("NonEmpty", "InCheckout")
      stateData match {
        case CartData(listOfItems) =>
          checkoutActor ! Common.CheckoutStarted(listOfItems.size, self)
        case _ =>
      }

    case InCheckout -> NonEmpty =>
      printMsg("InCheckout", "NonEmpty")
      setTimer(cartTimerName, CartTimerExpired, expirationTime)

    case InCheckout -> Empty =>
      printMsg("InCheckout", "Empty")

  }

  whenUnhandled {
    case Event(CartTimerExpired, _) =>
      println("  /CartTimerExpired/")
      goto(Empty) using CartData(listOfItems = List[String]())
    case Event(Common.CartIsEmpty, _) =>
      println("-- Something went wrong ! Received info that Cart is empty !")
      stay
    case Event(_event, _state) =>
      println("[WARN | Bad request] (" + _event + ", " + _state + ")")
      stay
  }

}
