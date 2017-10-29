package app

import akka.actor.{Actor, Props, Timers}
import app.Cart.{CheckoutClose, CheckoutStart, ItemAdd, ItemRemove}
import app.Checkout.DeliverySelect
import app.Common.{ActionCouldNotBeInvoked, Init, Terminate, printWarn}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

class Client extends Actor with Timers {
  private val customerActor = context.actorOf(Props(new Customer(self)), "customerActor")

  override def receive: Receive = {
    case Init =>
      context.system.scheduler.scheduleOnce(
        1.second, customerActor, ItemAdd("abc"))
      context.system.scheduler.scheduleOnce(
        1.5.second, customerActor, ItemAdd("def"))
      context.system.scheduler.scheduleOnce(
        2.second, customerActor, ItemAdd("ghi"))
      context.system.scheduler.scheduleOnce(
        2.5.second, customerActor, ItemRemove("ghi"))
      context.system.scheduler.scheduleOnce(
        7.second, customerActor, ItemAdd("xxx"))
      context.system.scheduler.scheduleOnce(
        8.second, customerActor, CheckoutStart)
      context.system.scheduler.scheduleOnce(
        9.second, customerActor, DeliverySelect)
      context.system.scheduler.scheduleOnce(
        10.second, customerActor, CheckoutClose)

    case ActionCouldNotBeInvoked(reason) =>
      printWarn("Action could not been invoked!", "\n> '" + reason + "'")
    case Terminate =>
      context.system.terminate()
    case _ =>
      printWarn("Bad request", "Client")
  }

}
