package app

import akka.actor.{Actor, ActorRef, Props, Timers}
import app.Cart._
import app.Common._

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
  private case object CheckoutTimerID
  private case object CheckoutTimerExpired
  private case object PaymentTimerID
  private case object PaymentTimerExpired
}

class Checkout(customerActor: ActorRef) extends Actor with Timers {

  import Checkout._

  private var paymentServiceActor: Option[ActorRef] = None
  private var cartActor: Option[ActorRef] = None

  def setCheckoutTimer(): Unit = {
    if (timers.isTimerActive(CheckoutTimerID))
      timers.cancel(CheckoutTimerID)
    timers.startSingleTimer(CheckoutTimerID, CheckoutTimerExpired, expirationTime)
  }

  def unsetCheckoutTimer(): Unit = {
    if (timers.isTimerActive(CheckoutTimerID))
      timers.cancel(CheckoutTimerID)
  }

  def setPaymentTimer(): Unit = {
    if (timers.isTimerActive(PaymentTimerID))
      timers.cancel(PaymentTimerID)
    timers.startSingleTimer(PaymentTimerID, PaymentTimerExpired, expirationTime)
  }

  def unsetPaymentTimer(): Unit = {
    if (timers.isTimerActive(PaymentTimerID))
      timers.cancel(PaymentTimerID)
  }

  def receive: Receive = selectingDelivery

  def selectingDelivery: Receive = {
    case CheckoutStarted(cartActor_, itemsCount) if itemsCount > 0 =>
      this.cartActor = Option(cartActor_)
      setCheckoutTimer()

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
      unsetCheckoutTimer()

    case DeliverySelect =>
      unsetCheckoutTimer()
      become_(context, selectingPayment, "SelectingDelivery", "SelectingPayment")
      setCheckoutTimer()

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
      unsetCheckoutTimer()

    case PaymentSelect =>
      unsetCheckoutTimer()
      become_(context, processingPayment, "SelectingPayment", "ProcessingPayment")
      if (paymentServiceActor.isEmpty)
        paymentServiceActor = Option(context.actorOf(
          Props(new PaymentService(customerActor)), "paymentServiceActor"))
      paymentServiceActor.get ! PaymentServiceStarted(self)
      customerActor ! PaymentServiceStarted(paymentServiceActor.get)
      setPaymentTimer()

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
      unsetPaymentTimer()
      become_(context, closed, "ProcessingPayment", "Closed")
      self ! Close

    case _ => printWarn("Bad request", "Checkout / processingPayment")
  }


  def closed: Receive = {
    case Close =>
      cartActor.get ! CheckoutClose
      become_(context, selectingDelivery, "Closed", "SelectingDelivery")

    case _ => printWarn("Bad request", "Checkout / closed")
  }

  def cancelled: Receive = {
    case Cancel =>
      cartActor.get ! CheckoutCancel
      become_(context, selectingDelivery, "Cancelled", "SelectingDelivery")

    case _ => printWarn("Bad request", "Checkout / cancelled")
  }

}
