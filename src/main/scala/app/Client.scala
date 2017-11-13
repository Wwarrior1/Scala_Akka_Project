package app

import java.net.URI

import akka.actor.{Actor, Props, Timers}
import app.CartManager.ItemAdd
import app.Common._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

class Client extends Actor with Timers {
  private val customerActor = context.actorOf(Props(new Customer(self)), "customerActor")

  private val uri_1 = new URI("12345")
  private val uri_2 = new URI("23456")

  private val item_1 = Item(uri_1, "milk", 3, 2)
  private val item_2 = Item(uri_2, "apple", 0.5, 10)

  override def receive: Receive = {
    case Init =>
      self ! Init1  // after that change (within 20 sec) to Init2 to notice effect.

    case Init1 =>
      context.system.scheduler.scheduleOnce(
        1.second, customerActor, ItemAdd(item_1))
      context.system.scheduler.scheduleOnce(
        1.5.second, customerActor, ItemAdd(item_2))
      context.system.scheduler.scheduleOnce(
        2.second, customerActor, Snap)
      context.system.scheduler.scheduleOnce(
        2.5.second, customerActor, Terminate)

    case Init2 =>
      context.system.scheduler.scheduleOnce(
        1.second, customerActor, CheckState)

    case ActionCouldNotBeInvoked(reason) =>
      printWarn("Action could not been invoked!", "\n> '" + reason + "'")
    case Terminate =>
      context.system.terminate()
    case _ =>
      printWarn("Bad request", "Client")
  }

}
