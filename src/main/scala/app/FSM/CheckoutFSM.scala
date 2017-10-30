package app.FSM

import akka.actor.{ActorRef, FSM, Props}
import app.Cart.{CartIsEmpty, CheckoutCancel, CheckoutClosed, CheckoutStarted}
import app.Common._

/**
  * Created by Wojciech Baczyński on 22.10.17.
  */

object CheckoutFSM {
  def props(customerActor: ActorRef): Props = Props(new CheckoutFSM(customerActor))

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

class CheckoutFSM(customerActor: ActorRef) extends FSM[State, Data] {

  import CheckoutFSM._

  startWith(SelectingDelivery, CheckoutData(originalSender = None))

  when(SelectingDelivery) {
    case Event(CheckoutStarted(newOriginalSender, itemsCount), _) =>
      if (itemsCount == 0)
        newOriginalSender ! CartIsEmpty
      setTimer(checkoutTimerName, CheckoutTimerExpired, expirationTime)
      stay // TODO - repair: using CheckoutData(Some(newOriginalSender))

    case Event(CheckoutCancel, _) =>
      goto(Cancelled)

    case Event(DeliverySelected, _) =>
      goto(SelectingPayment)
  }

  when(SelectingPayment) {
    case Event(CheckoutCancel, _) =>
      goto(Cancelled)

    case Event(PaymentSelected, _) =>
      goto(ProcessingPayment)
  }

  when(ProcessingPayment) {
    case Event(CheckoutCancel, _) =>
      goto(Cancelled)

    case Event(PaymentReceived, _) =>
      goto(Closed)
  }

  when(Closed) {
    case Event(Close, CheckoutData(originalSender)) =>
      println("-- Closed !")
      originalSender.get ! CheckoutClosed
      stay
  }

  when(Cancelled) {
    case Event(Cancel, CheckoutData(originalSender)) =>
      println("-- Cancelled !")
      originalSender.get ! CheckoutCancel
      stay
  }

  onTransition {
    case SelectingDelivery -> Cancelled =>
      printTransition("SelectingDelivery", "Cancelled")

    case SelectingDelivery -> SelectingPayment =>
      printTransition("SelectingDelivery", "SelectingPayment")

    case SelectingPayment -> Cancelled =>
      printTransition("SelectingPayment", "Cancelled")

    case SelectingPayment -> ProcessingPayment =>
      cancelTimer(checkoutTimerName)
      printTransition("SelectingPayment", "ProcessingPayment")
      setTimer(paymentTimerName, PaymentTimerExpired, expirationTime)

    case ProcessingPayment -> Cancelled =>
      printTransition("ProcessingPayment", "Cancelled")

    case ProcessingPayment -> Closed =>
      cancelTimer(paymentTimerName)
      printTransition("ProcessingPayment", "Closed")

  }

  whenUnhandled {
    case Event(CheckoutTimerExpired, _) =>
      printTimerExpired("CheckoutTimer")
      goto(Cancelled)
    case Event(PaymentTimerExpired, _) =>
      printTimerExpired("PaymentTimer")
      goto(Cancelled)
    case Event(_event, _state) =>
      printWarn("Bad request", "" + _event + ", " + _state)
      stay
  }

}
