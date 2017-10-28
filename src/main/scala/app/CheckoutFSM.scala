package app

import akka.actor.{ActorRef, FSM, Props}
import app.Common._

/**
  * Created by Wojciech Baczyński on 22.10.17.
  */

object CheckoutFSM {
  def props: Props = Props(new Checkout)

  // States
  private case object SelectingDelivery extends State
  private case object SelectingPayment extends State
  private case object ProcessingPayment extends State
  private case object Closed extends State
  private case object Cancelled extends State

  // Data
  private case class CheckoutData(originalSender: Option[ActorRef]) extends Data

  // Messages
  private final case object Close
  private final case class Cancel(msg: String)
  private final case object DeliverySelected
  private final case object PaymentSelected
  private final case object PaymentReceived
  private case object CheckoutTimerExpired
  private case object PaymentTimerExpired

}

class CheckoutFSM extends FSM[State, Data] {

  import CheckoutFSM._

  startWith(SelectingDelivery, CheckoutData(originalSender = None))

  when(SelectingDelivery) {
    case Event(Common.CheckoutStarted(itemsCount, newOriginalSender), _) =>
      if (itemsCount == 0)
        newOriginalSender ! Common.CartIsEmpty
      setTimer(checkoutTimerName, CheckoutTimerExpired, expirationTime)
      stay using CheckoutData(Some(newOriginalSender))

    case Event(Common.CheckoutCancelled, _) =>
      goto(Cancelled)

    case Event(DeliverySelected, _) =>
      goto(SelectingPayment)
  }

  when(SelectingPayment) {
    case Event(Common.CheckoutCancelled, _) =>
      goto(Cancelled)

    case Event(PaymentSelected, _) =>
      goto(ProcessingPayment)
  }

  when(ProcessingPayment) {
    case Event(Common.CheckoutCancelled, _) =>
      goto(Cancelled)

    case Event(PaymentReceived, _) =>
      goto(Closed)
  }

  when(Closed) {
    case Event(Close, CheckoutData(originalSender)) =>
      println("-- Closed !")
      originalSender.get ! Common.CheckoutClosed
      stay
  }

  when(Cancelled) {
    case Event(Cancel, CheckoutData(originalSender)) =>
      println("-- Cancelled !")
      originalSender.get ! Common.CheckoutCancelled
      stay
  }

  onTransition {
    case SelectingDelivery -> Cancelled =>
      printMsg("SelectingDelivery", "Cancelled")

    case SelectingDelivery -> SelectingPayment =>
      printMsg("SelectingDelivery", "SelectingPayment")

    case SelectingPayment -> Cancelled =>
      printMsg("SelectingPayment", "Cancelled")

    case SelectingPayment -> ProcessingPayment =>
      cancelTimer(checkoutTimerName)
      printMsg("SelectingPayment", "ProcessingPayment")
      setTimer(paymentTimerName, PaymentTimerExpired, expirationTime)

    case ProcessingPayment -> Cancelled =>
      printMsg("ProcessingPayment", "Cancelled")

    case ProcessingPayment -> Closed =>
      cancelTimer(paymentTimerName)
      printMsg("ProcessingPayment", "Closed")

  }

  whenUnhandled {
    case Event(CheckoutTimerExpired, _) =>
      println("  /CheckoutTimerExpired/")
      goto(Cancelled)
    case Event(PaymentTimerExpired, _) =>
      println("  /PaymentTimerExpired/")
      goto(Cancelled)
    case Event(_event, _state) =>
      println("[WARN | Bad request] (" + _event + ", " + _state + ")")
      stay
  }

}
