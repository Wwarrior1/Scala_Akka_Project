package app

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef}
import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Common {
  final case class CheckoutStarted(itemsCount: Int, sender: ActorRef)
  final case object CheckoutCancelled
  final case object CheckoutClosed
  final case object CartIsEmpty

  final def become_(context: ActorContext, receive: Receive, msg: String): Unit = {
    context.become(receive)
    println("  [-> " + msg + "]")
  }

  final def printMsg(from: String, to: String): Unit = {
    println("  [" + from + " -> " + to + "]")
  }

  val expirationTime: FiniteDuration = 4.seconds
  val cartTimerName: String = "CartTimer"
  val checkoutTimerName: String = "CheckoutTimer"
  val paymentTimerName: String = "PaymentTimer"

  trait State
  trait Data
}