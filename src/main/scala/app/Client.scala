package app

import java.net.URI

import akka.actor.{Actor, Props, Timers}
import app.CartManager.{CheckoutStart, ItemAdd, ItemRemove}
import app.Checkout.{DeliverySelect, PaymentSelect}
import app.Common.{ActionCouldNotBeInvoked, Init, Terminate, printWarn}
import app.PaymentService.{DoPayment, PayPal, PayU, Visa}
import app.ProductCatalog.SearchItem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

class Client extends Actor with Timers {
  private val customerActor = context.actorOf(Props(new Customer(self)), "customerActor")

  private val uri_1 = new URI("12345")
  private val uri_2 = new URI("23456")
  private val uri_3 = new URI("34567")

  private val item_1 = Item(uri_1, "milk", "shop", 3, 2)
  private val item_2 = Item(uri_2, "apple", "shop", 0.5, 10)
  private val item_3 = Item(uri_3, "bread", "shop", 2.5, 1)

  override def receive: Receive = {
    case Init =>
//      context.system.scheduler.scheduleOnce(
//        1.second, customerActor, SearchItem("fanta"))
//        1.second, customerActor, SearchItem("walnuts emerald roasted"))
//        1.second, customerActor, SearchItem("shouldReturnNotFound"))
//        1.second, customerActor, SearchItem("walnuts emerald roasted glazed salt with sea tree bag jar cork Hawaiian company"))

      context.system.scheduler.scheduleOnce(
        1.second, customerActor, ItemAdd(item_1))
//      context.system.scheduler.scheduleOnce(
//        1.5.second, customerActor, ItemAdd(item_2))
//      context.system.scheduler.scheduleOnce(
//        2.second, customerActor, ItemAdd(item_3))
//      context.system.scheduler.scheduleOnce(
//        2.5.second, customerActor, ItemRemove(item_2, 5))
//      context.system.scheduler.scheduleOnce(
//        8.second, customerActor, ItemAdd(item_1))
      context.system.scheduler.scheduleOnce(
        1.5.second, customerActor, CheckoutStart)

      context.system.scheduler.scheduleOnce(
        2.second, customerActor, DeliverySelect)
      context.system.scheduler.scheduleOnce(
        2.5.second, customerActor, PaymentSelect)

//      context.system.scheduler.scheduleOnce(
//        3.second, customerActor, DoPayment(PayPal))
//      context.system.scheduler.scheduleOnce(
//        4.second, customerActor, DoPayment(PayU))
      context.system.scheduler.scheduleOnce(
        5.second, customerActor, DoPayment(Visa))

      context.system.scheduler.scheduleOnce(
        20.second, self, Terminate)

    case ActionCouldNotBeInvoked(reason) =>
      printWarn("Action could not been invoked!", "\n> '" + reason + "'")
    case Terminate =>
      context.system.terminate()
    case _ =>
      printWarn("Bad request", "Client")
  }

}
