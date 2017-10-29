package app.FSM

import akka.actor.{ActorRef, FSM, Props}
import app.Cart.{CartIsEmpty, CheckoutCancel, CheckoutClosed, CheckoutStarted}
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
      goto(Empty) // TODO - repair: using CartData(listOfItems = List[String]())
    case Event(ItemAdded(newItem), CartData(listOfItems)) =>
      println("(0)")
      val newList = listOfItems :+ newItem
      println("(" + newList.size + ") " + newList)
      goto(NonEmpty) // TODO - repair: using CartData(newList)
  }

  when(NonEmpty) {
    case Event(ItemAdded(newItem), CartData(listOfItems)) =>
      val newList = listOfItems :+ newItem
      println("(" + newList.size + ") " + newList)
      stay // TODO - repair: using CartData(newList)

    case Event(ItemRemoved(oldItem), CartData(listOfItems)) =>
      if (!listOfItems.contains(oldItem)) {
        printWarn("Tried to remove not existing element")
        stay
      } else {
        val newList = listOfItems.filter(_ != oldItem)
        println("(" + newList.size + ") " + newList)
        if (newList.nonEmpty)
          stay // TODO - repair: using CartData(newList)
        else
          goto(Empty) // TODO - repair: using CartData(newList)
      }

    case Event(CheckoutStarted, CartData(_)) =>
      println("Checkout started")
      goto(InCheckout)
  }

  when(InCheckout) {
    case Event(CheckoutCancel, _) =>
      println("-- Checkout cancelled")
      goto(NonEmpty)

    case Event(CheckoutClosed, _) =>
      println("-- Checkout closed")
      goto(Empty) // TODO - repair: using CartData(List[String]())
  }

  onTransition {
    case Empty -> NonEmpty =>
      printTransition("Empty", "NonEmpty")
      setTimer(cartTimerName, CartTimerExpired, expirationTime)

    case NonEmpty -> Empty =>
      printTransition("NonEmpty", "Empty")

    case NonEmpty -> InCheckout =>
      cancelTimer(cartTimerName)
      printTransition("NonEmpty", "InCheckout")
      stateData match {
        case CartData(listOfItems) =>
          checkoutActor ! CheckoutStarted(self, listOfItems.size)
        case _ =>
      }

    case InCheckout -> NonEmpty =>
      printTransition("InCheckout", "NonEmpty")
      setTimer(cartTimerName, CartTimerExpired, expirationTime)

    case InCheckout -> Empty =>
      printTransition("InCheckout", "Empty")

  }

  whenUnhandled {
    case Event(CartTimerExpired, _) =>
      printTimerExpired("CartTimer")
      goto(Empty) // TODO - repair: using CartData(List[String]())
    case Event(CartIsEmpty, _) =>
      println("-- Something went wrong ! Received info that Cart is empty !")
      stay
    case Event(_event, _state) =>
      printWarn("Bad request", "" + _event + ", " + _state)
      stay
  }

}
