package app

import akka.actor.{ActorRef, Props, Timers}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import app.CartManager._
import app.Common._

import scala.concurrent.duration.{FiniteDuration, _}

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Checkout {

  // Messages
  final case object DeliverySelect
  final case object PaymentReceived
  final case object PaymentSelect
  final case class PaymentServiceStarted(actor: ActorRef)

  // Messages - helpers
  final case object Close
  final case object Cancel

  // Messages for Timers
  private case object CheckoutTimerID extends TimerID
  private case object CheckoutTimerExpired
  private case object PaymentTimerID extends TimerID
  private case object PaymentTimerExpired

}

class Checkout(customerActor: ActorRef, id: String = System.currentTimeMillis().toString)
  extends PersistentActor with Timers {

  import Checkout._

  private var paymentServiceActor: Option[ActorRef] = None
  private var cartActor: Option[ActorRef] = None

  def setTimer(timerID: TimerID): Unit = {
    persist(TimerEvent(timerID, System.currentTimeMillis() + expirationTime.toMillis)) { _ =>
      unsetTimer(timers, timerID)
      if (timerID == CheckoutTimerID)
        timers.startSingleTimer(CheckoutTimerID, CheckoutTimerExpired, expirationTime)
      else if (timerID == PaymentTimerID)
        timers.startSingleTimer(PaymentTimerID, PaymentTimerExpired, expirationTime)
    }
  }

  def saveSnap(actualState: Receive): Unit = {
    saveSnapshot(actualState)
    print("\033[36m" + "  // SNAPSHOT SAVED //" + "\033[0m  ")
  }

  // PERSISTENCE

  def persistenceId: String = "Checkout_" + id

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      println("\033[36m" + "  // RECOVER COMPLETED //" + "\033[0m  ")
    case TimerEvent(timerID, endTime) =>
      val newExpirationTime: FiniteDuration =
        (endTime - System.currentTimeMillis()).millis
      if (newExpirationTime.toMillis > 0) {
        unsetTimer(timers, timerID)
        if (timerID == CheckoutTimerID) {
          println("\033[36m" + "  // RECOVERED CheckoutTimer //" + "\033[0m  ")
          timers.startSingleTimer(CheckoutTimerID, CheckoutTimerExpired, newExpirationTime)
        } else if (timerID == PaymentTimerID) {
          println("\033[36m" + "  // RECOVERED PaymentTimer //" + "\033[0m  ")
          timers.startSingleTimer(PaymentTimerID, PaymentTimerExpired, newExpirationTime)
        }
      }
      println("\033[32m" + newExpirationTime.toSeconds + "\033[0m  ")
    case SnapshotOffer(_, actualState: Receive) =>
      println("YOLO !")
      self ! Start(actualState)
  }

  override def receiveCommand: Receive = selectingDelivery


  def selectingDelivery: Receive = {
    case Start(actualState) =>
      become_(context, actualState, "SNAP", actualState.getClass.getName)

    case CheckoutStarted(cartActor_, itemsCount) if itemsCount > 0 =>
      this.cartActor = Option(cartActor_)
      setTimer(CheckoutTimerID)

    case CheckoutStarted(cartActor_, itemsCount) if itemsCount == 0 =>
      this.cartActor = Option(cartActor_)
      this.cartActor.get ! CartIsEmpty

    case CheckoutTimerExpired =>
      become_(context, cancelled, "SelectingDelivery", "Cancelled")
      printTimerExpired("CheckoutTimer")
      self ! Cancel

    case CheckoutCancel =>
      become_(context, cancelled, "SelectingDelivery", "Cancelled")
      self ! Cancel
      unsetTimer(timers, CheckoutTimerID)

    case DeliverySelect =>
      unsetTimer(timers, CheckoutTimerID)
      become_(context, selectingPayment, "SelectingDelivery", "SelectingPayment")
      setTimer(CheckoutTimerID)

    case Snap => saveSnap(selectingDelivery)
    case CheckState => customerActor ! CheckState("Empty")
    case _ => printWarn("Bad request", "Checkout / selectingDelivery")
  }

  def selectingPayment: Receive = {
    case CheckoutTimerExpired =>
      become_(context, cancelled, "SelectingPayment", "Cancelled")
      printTimerExpired("CheckoutTimer")
      self ! Cancel

    case CheckoutCancel =>
      become_(context, cancelled, "SelectingPayment", "Cancelled")
      self ! Cancel
      unsetTimer(timers, CheckoutTimerID)

    case PaymentSelect =>
      unsetTimer(timers, CheckoutTimerID)
      become_(context, processingPayment, "SelectingPayment", "ProcessingPayment")
      if (paymentServiceActor.isEmpty)
        paymentServiceActor = Option(context.actorOf(
          Props(new PaymentService(customerActor)), "paymentServiceActor"))
      paymentServiceActor.get ! PaymentServiceStarted(self)
      customerActor ! PaymentServiceStarted(paymentServiceActor.get)
      setTimer(PaymentTimerID)

    case Snap => saveSnap(selectingPayment)
    case CheckState => customerActor ! CheckState("SelectingPayment")
    case _ => printWarn("Bad request", "Checkout / selectingPayment")
  }

  def processingPayment: Receive = {
    case PaymentTimerExpired =>
      become_(context, cancelled, "ProcessingPayment", "Cancelled")
      printTimerExpired("PaymentTimer")
      self ! Cancel

    case CheckoutCancel =>
      become_(context, cancelled, "ProcessingPayment", "Cancelled")
      self ! Cancel

    case PaymentReceived =>
      unsetTimer(timers, PaymentTimerID)
      become_(context, closed, "ProcessingPayment", "Closed")
      self ! Close

    case Snap => saveSnap(processingPayment)
    case CheckState => customerActor ! CheckState("ProcessingPayment")
    case _ => printWarn("Bad request", "Checkout / processingPayment")
  }


  def closed: Receive = {
    case Close =>
      cartActor.get ! CheckoutClose
      become_(context, selectingDelivery, "Closed", "SelectingDelivery")

    case Snap => saveSnap(closed)
    case CheckState => customerActor ! CheckState("Closed")
    case _ => printWarn("Bad request", "Checkout / closed")
  }

  def cancelled: Receive = {
    case Cancel =>
      cartActor.get ! CheckoutCancel
      become_(context, selectingDelivery, "Cancelled", "SelectingDelivery")

    case Snap => saveSnap(cancelled)
    case CheckState => customerActor ! CheckState("Cancelled")
    case _ => printWarn("Bad request", "Checkout / cancelled")
  }

}
