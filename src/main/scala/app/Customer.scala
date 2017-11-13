package app

import akka.actor.{Actor, ActorRef, Props, Timers}
import app.CartManager._
import app.Checkout.{DeliverySelect, PaymentSelect, PaymentServiceStarted}
import app.Common._
import app.PaymentService.{DoPayment, PaymentConfirmed}

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Customer {
  final case object Terminate
}

class Customer(clientActor: ActorRef) extends Actor with Timers {

  private val cartActor = context.actorOf(Props(
    new CartManager(self, new Cart(Map.empty), "1")), "cartActor")

  private var checkoutActor: Option[ActorRef] = None
  private var paymentServiceActor: Option[ActorRef] = None

  override def receive: Receive = {
    case Snap =>
      cartActor ! Snap

    case CheckState =>
      cartActor ! CheckState

    case CheckState(stateName) =>
      println("\033[33m" + " >> STATE: " + stateName + "\033[0m")

    case ItemAdd(newItem) =>
      cartActor ! ItemAdd(newItem)

    case ItemRemove(oldItem, count) =>
      cartActor ! ItemRemove(oldItem, count)

    case CheckoutStart =>
      cartActor ! CheckoutStart

    case CheckoutCancel =>
      if (sender == checkoutActor.get)
        checkoutActor.get ! CheckoutCancel
      else
        cartActor ! CheckoutCancel

    case CheckoutClose =>
      cartActor ! CheckoutClose

    case DeliverySelect =>
      if (checkoutActor.isEmpty)
        clientActor ! ActionCouldNotBeInvoked("'Checkout' actor has not been initialized yet")
      else
        checkoutActor.get ! DeliverySelect

    case PaymentSelect =>
      if (checkoutActor.isEmpty)
        clientActor ! ActionCouldNotBeInvoked("'Checkout' actor has not been initialized yet")
      else
        checkoutActor.get ! PaymentSelect

    case DoPayment =>
      if (paymentServiceActor.isEmpty)
        clientActor ! ActionCouldNotBeInvoked("'Payment Service' actor has not been initialized yet")
      else
        paymentServiceActor.get ! DoPayment

    // New actor refs

    case CheckoutStarted(newCheckoutActor, _) =>
      this.checkoutActor = Option(newCheckoutActor)
      println("\033[32m" + "CHECKOUT STARTED" + "\033[0m")
    case CheckoutClosed =>
      println("\033[32m" + "CHECKOUT CLOSED" + "\033[0m")
    case CheckoutCancelled =>
      println("\033[32m" + "CHECKOUT CANCELLED" + "\033[0m")
    case PaymentServiceStarted(newPaymentServiceActor) =>
      this.paymentServiceActor = Option(newPaymentServiceActor)
      println("\033[32m" + "PAYMENT SERVICE STARTED" + "\033[0m")
    case PaymentConfirmed =>
      println("\033[32m" + "PAYMENT CONFIRMED" + "\033[0m")
    case CartIsEmpty =>
      println("\033[32m" + "CART IS EMPTY" + "\033[0m")

    case ActionCouldNotBeInvoked(reason) =>
      printWarn("Action could not been invoked!", "\n> '" + reason + "'")
    case Terminate =>
      context.system.terminate()
    case _ =>
      printWarn("Bad request", "Customer")
  }

}
