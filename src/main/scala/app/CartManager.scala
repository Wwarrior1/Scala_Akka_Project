package app

import java.net.URI

import akka.actor.{ActorRef, Props, Timers}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import app.Common._

import scala.concurrent.duration.{FiniteDuration, _}

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object CartManager {

  // Messages
  final case class ItemAdd(newItem: Item)
  final case class ItemRemove(oldItem: Item, count: Int)
  final case object CartIsEmpty
  final case object CheckoutStart
  final case class CheckoutStarted(actor: ActorRef, itemsCount: Int)
  final case object CheckoutCancel
  final case object CheckoutCancelled
  final case class CheckoutClose()
  final case object CheckoutClosed

  // Messages for Timers
  final case object CartTimerID extends TimerID
  final case object CartTimerExpired

  // Persistence
  final case class ItemAddEvent(newItem: Item) extends Event
  final case class ItemRemoveEvent(oldItem: Item, count: Int) extends Event
  final case object CartClearEvent extends Event

  final case class CartManagerEvent(event: Event)

}

class CartManager(customerActor: ActorRef, var shoppingCart: Cart, id: String = System.currentTimeMillis().toString)
  extends PersistentActor with Timers {

  import CartManager._

  def this(customerActor: ActorRef) = this(customerActor, Cart.empty)

  def this(customerActor: ActorRef, id: String) = this(customerActor, Cart.empty, id)

  private var checkoutActor: Option[ActorRef] = None

  def getListOfItems: Map[URI, Item] = shoppingCart.items

  // TIMERS

  def setCartTimer(time: FiniteDuration = expirationTime): Unit = {
    persist(TimerEvent(CartTimerID, System.currentTimeMillis() + expirationTime.toMillis)) { _ =>
      unsetTimer(timers, CartTimerID)
      timers.startSingleTimer(CartTimerID, CartTimerExpired, expirationTime)
    }
  }

  def saveSnap(actualState: Receive): Unit = {
    saveSnapshot(Snapshot(shoppingCart, actualState))
    print("\033[36m" + "  // SNAPSHOT SAVED //" + "\033[0m  ")
    println("(" + getListOfItems.size + ") " + getListOfItems)
  }

  // PERSISTENCE

  def persistenceId: String = "CartManager_" + id

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      println("\033[36m" + "  // RECOVER COMPLETED //" + "\033[0m  ")
    case CartManagerEvent(event) => event match {
      case ItemAddEvent(newItem) =>
        println("\033[36m" + "  // RECOVERED CartManagerEvent 1//" + "\033[0m  ")
        shoppingCart = shoppingCart.addItem(newItem)
      case ItemRemoveEvent(oldItem, count) =>
        println("\033[36m" + "  // RECOVERED CartManagerEvent 2//" + "\033[0m  ")
        try {
          shoppingCart = shoppingCart.removeItem(oldItem, count)
        } catch {
          case e: ActionCouldNotBeInvokedException =>
            customerActor ! ActionCouldNotBeInvoked(e.message)
        }
      case CartClearEvent =>
        println("\033[36m" + "  // RECOVERED CartManagerEvent 3//" + "\033[0m  ")
        shoppingCart = shoppingCart.clearCart()
      case _ =>
        println("\033[36m" + "  // RECOVERED CartManagerEvent 4//" + "\033[0m  ")
    }
    case TimerEvent(CartTimerID, endTime) =>
      val newExpirationTime: FiniteDuration =
        (endTime - System.currentTimeMillis()).millis
      if (newExpirationTime.toMillis > 0) {
        println("\033[36m" + "  // RECOVERED CartTimer //" + "\033[0m  ")
        unsetTimer(timers, CartTimerID)
        timers.startSingleTimer(CartTimerID, CartTimerExpired, newExpirationTime)
      }
      println("\033[32m" + newExpirationTime.toSeconds + "\033[0m  ")
    case SnapshotOffer(_, Snapshot(shoppingCart_, actualState)) =>
      println("YOLO !")
      shoppingCart = shoppingCart_
      println("(" + getListOfItems.size + ") " + getListOfItems)
      self ! Start(actualState)
    case SnapshotOffer(metadata, snapshot) =>
      println("YOLO !!!")
  }

  override def receiveCommand: Receive = empty

  // STATES

  def empty: Receive = {
    case Start(actualState) =>
      become_(context, actualState, "SNAP", actualState.getClass.getName)

    case ItemAdd(newItem) =>
      setCartTimer()
      persist(CartManagerEvent(ItemAddEvent(newItem))) { _ =>
        shoppingCart = shoppingCart.addItem(newItem)
        println("(" + getListOfItems.size + ") " + getListOfItems)
        become_(context, nonEmpty, "Empty", "NonEmpty")
      }

    case Snap => saveSnap(empty)
    case CheckState => customerActor ! CheckState("Empty")
    case _ => printWarn("Bad request", "CartManager / empty")
  }

  def nonEmpty: Receive = {
    case CartTimerExpired =>
      persist(CartManagerEvent(CartClearEvent)) { _ =>
        shoppingCart = shoppingCart.clearCart()
        printTimerExpired("CartTimer")
        become_(context, empty, "NonEmpty", "Empty")
        customerActor ! CartIsEmpty
      }

    case ItemAdd(newItem) =>
      setCartTimer()
      persist(CartManagerEvent(ItemAddEvent(newItem))) { _ =>
        shoppingCart = shoppingCart.addItem(newItem)
        println("(" + getListOfItems.size + ") " + getListOfItems)
      }

    case ItemRemove(oldItem, count) =>
      setCartTimer()
      persist(CartManagerEvent(ItemRemoveEvent(oldItem, count))) { _ =>
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
      }

    case CheckoutStart =>
      unsetTimer(timers, CartTimerID)
      become_(context, inCheckout, "NonEmpty", "InCheckout")
      if (checkoutActor.isEmpty)
        checkoutActor = Option(context.actorOf(
          Props(new Checkout(customerActor)), "checkoutActor"))
      checkoutActor.get ! CheckoutStarted(self, getListOfItems.size)
      customerActor ! CheckoutStarted(checkoutActor.get, getListOfItems.size)

    case Snap => saveSnap(nonEmpty)
    case CheckState => customerActor ! CheckState("NonEmpty")
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

    case Snap => saveSnap(inCheckout)
    case CheckState => customerActor ! CheckState("InCheckout")
    case _ => printWarn("Bad request", "CartManager / inCheckout")
  }
}
