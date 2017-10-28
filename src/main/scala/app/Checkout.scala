package app

import akka.actor.{Actor, ActorRef, Props, Timers}
import app.Common._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Checkout {
  def props: Props = Props(new Checkout)

  private final case object Close
  private final case class Cancel(msg: String)
  private final case object DeliverySelected
  private final case object PaymentSelected
  private final case object PaymentReceived

  // Messages for Timers
  private case object CheckoutTimerID
  private case object CheckoutTimerExpired
  private case object PaymentTimerID
  private case object PaymentTimerExpired
}

class Checkout extends Actor with Timers {

  import Checkout._

  private var originalSender: Option[ActorRef] = None

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
    case Common.CheckoutStarted(itemsCount, originalSender_) if itemsCount > 0 =>
      originalSender = Some(originalSender_)
      setCheckoutTimer()

    case Common.CheckoutStarted(itemsCount, originalSender_) if itemsCount == 0 =>
      originalSender = Some(originalSender_)
      originalSender.get ! Common.CartIsEmpty

    case CheckoutTimerExpired | Common.CheckoutCancelled =>
      become_(context, cancelled, "Cancelled")
      self ! Cancel("CheckoutTimerExpired")

    case DeliverySelected =>
      become_(context, selectingPayment, "SelectingPayment")

    case _ => println("[WARN | Bad request] (Checkout / selectingDelivery)")
  }

  def selectingPayment: Receive = {
    case CheckoutTimerExpired | Common.CheckoutCancelled =>
      become_(context, cancelled, "Cancelled")
      self ! Cancel("CheckoutTimerExpired")

    case PaymentSelected =>
      unsetCheckoutTimer()
      become_(context, processingPayment, "ProcessingPayment")
      setPaymentTimer()

    case _ => println("[WARN | Bad request] (Checkout / selectingPayment)")
  }

  def processingPayment: Receive = {
    case PaymentTimerExpired | Common.CheckoutCancelled =>
      become_(context, cancelled, "Cancelled")
      self ! Cancel("PaymentTimerExpired")

    case PaymentReceived =>
      unsetPaymentTimer()
      become_(context, closed, "Closed")
      self ! Close

    case _ => println("[WARN | Bad request] (Checkout / processingPayment)")
  }


  def closed: Receive = {
    case Close =>
      println("  /timeout/ Closed !")
      originalSender.get ! Common.CheckoutClosed
      become_(context, selectingDelivery, "SelectingDelivery")

    case _ => println("[WARN | Bad request] (Checkout / closed)")
  }

  def cancelled: Receive = {
    case Cancel(msg) =>
      println("  /" + msg + "/ Cancelled !")
      originalSender.get ! Common.CheckoutCancelled
      become_(context, selectingDelivery, "SelectingDelivery")

    case _ => println("[WARN | Bad request] (Checkout / cancelled)")
  }

}
