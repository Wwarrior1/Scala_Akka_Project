package app

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSelection, OneForOneStrategy, Props, SupervisorStrategy, Timers}
import app.CartManager._
import app.Checkout.{DeliverySelect, PaymentNotReceived, PaymentSelect, PaymentServiceStarted}
import app.Common._
import app.PaymentService._
import app.ProductCatalog.{ItemNotFound, ItemsFound, SearchItem}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.Duration

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Customer {
  final case object Terminate
}

class Customer(clientActor: ActorRef) extends Actor with Timers {

  val config: Config = ConfigFactory.load()
  val productCatalogActor: ActorSelection =
    context.actorSelection("akka.tcp://database@127.0.0.1:2552/user/productCatalogActor")

  private val cartActor = context.actorOf(Props(
    new CartManager(self, new Cart(Map.empty))), "cartActor")

  private var checkoutActor: Option[ActorRef] = None
  private var paymentServiceActor: Option[ActorRef] = None

  override def receive: Receive = {
    case SearchItem(query) =>
      println("\033[33mSearching: \033[33;1m" + query + "\033[33;0m ...\033[0m")
      productCatalogActor ! SearchItem(query)

    case ItemsFound(items, time) =>
      println("\033[33mFound items in time: \033[33;1m" + time + "\033[33m seconds\033[0m")
      items.foreach(item => println("\033[34m" +
        item._1 + " | " + item._2.name + " / " + item._2.brand + "; " + item._2.price + "; " + item._2.count + "\033[0m"))

    case ItemNotFound =>
      println("\033[33mItem \033[33;1mnot\033[33;0m found !\033[0m")

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

    case DoPayment(target: PaymentTarget) =>
      if (paymentServiceActor.isEmpty)
        clientActor ! ActionCouldNotBeInvoked("'Payment Service' actor has not been initialized yet")
      else
        paymentServiceActor.get ! DoPayment(target)

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
    case PaymentRefused =>
      println("\033[32m" + "PAYMENT REFUSED" + "\033[0m")
    case CartIsEmpty =>
      println("\033[32m" + "CART IS EMPTY" + "\033[0m")

    case ActionCouldNotBeInvoked(reason) =>
      printWarn("Action could not been invoked!", "\n> '" + reason + "'")
    case _ =>
      printWarn("Bad request", "Customer")
  }

}
