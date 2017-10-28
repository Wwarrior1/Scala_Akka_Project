package app

import akka.actor.{Actor, Props, Timers}
import app.Checkout.DeliverySelected
import app.Client.Terminate
import app.Common.{Init, NewActorCreated}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Client {
  def props: Props = Props(new Client)

  final case object Terminate

}

class Client extends Actor with Timers {
  private val cartActor = context.actorOf(Cart.props, "cartActor")
//  private val checkoutActor = context.actorOf(Props[Checkout], "checkoutActor")
//  private val checkoutActorFSM = context.actorOf(Props[CheckoutFSM], "checkoutActorFSM")
//  private val cartActorFSM = context.actorOf(CartFSM.props(checkoutActorFSM), "cartActorFSM")
//  private val delay = 0.seconds

  override def receive: Receive = {
    case Init =>
      context.system.scheduler.scheduleOnce(
        0.second, cartActor, Init)

    case NewActorCreated(checkoutActor) =>
      context.system.scheduler.scheduleOnce(
        1.second, cartActor, Cart.ItemAdded("abc"))
      context.system.scheduler.scheduleOnce(
        1.5.second, cartActor, Cart.ItemAdded("def"))
      context.system.scheduler.scheduleOnce(
        2.second, cartActor, Cart.ItemAdded("ghi"))
      context.system.scheduler.scheduleOnce(
        2.5.second, cartActor, Cart.ItemRemoved("ghi"))
      context.system.scheduler.scheduleOnce(
        7.second, cartActor, Cart.ItemAdded("xxx"))
      context.system.scheduler.scheduleOnce(
        8.second, cartActor, Common.CheckoutStarted)
      context.system.scheduler.scheduleOnce(
        9.second, checkoutActor, DeliverySelected)



//      context.system.scheduler.scheduleOnce(
//      delay - 0.5.second, cartActorFSM, CartFSM.Init)
//
//      context.system.scheduler.scheduleOnce(
//        delay + 0.second, cartActorFSM, CartFSM.ItemAdded("abc"))
//      context.system.scheduler.scheduleOnce(
//        delay + 0.5.second, cartActorFSM, CartFSM.ItemAdded("def"))
//      context.system.scheduler.scheduleOnce(
//        delay + 1.second, cartActorFSM, CartFSM.ItemAdded("ghi"))
//      context.system.scheduler.scheduleOnce(
//        delay + 1.5.second, cartActorFSM, CartFSM.ItemRemoved("ghi"))
//      context.system.scheduler.scheduleOnce(
//        delay + 6.second, cartActorFSM, CartFSM.ItemAdded("xxx"))
//      context.system.scheduler.scheduleOnce(
//        delay + 7.second, cartActorFSM, Common.CheckoutStarted)

    case Terminate =>
      context.system.terminate()
    case Common.CartIsEmpty =>
      println("Cannot go ahead - Cart is empty !")
    case _ =>
      println("[WARN | Bad request] (Client)")
  }

}
