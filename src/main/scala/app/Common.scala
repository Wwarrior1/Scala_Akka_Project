package app

import akka.actor.Actor.Receive
import akka.actor.ActorContext

import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 19.10.17.
  */

object Common {

  final case object Init
  final case object Terminate
  final case class ActionCouldNotBeInvoked(reason: String)

  var expirationTime: FiniteDuration = 5.seconds

  final def become_(context: ActorContext, receive: Receive, from: String, to: String): Unit = {
    context.become(receive)
    printTransition(from, to)
  }

  final def printTransition(from: String, to: String): Unit = {
    println("\033[34m" + "  [" + from + " -> " + to + "]" + "\033[0m")
  }

  final def printTimerExpired(timerName: String): Unit = {
    println("\033[35m" + "  // " + timerName + " expired //" + "\033[0m")
  }

  final def printWarn(mainReason: String, description: String = ""): Unit = {
    if (description != "")
      println("\033[33m" + "[WARN | " + mainReason + "] (" + description + ")" + "\033[0m")
    else
      println("\033[33m" + "[WARN | " + mainReason + "]" + "\033[0m")
  }

  // FSM
  trait State
  trait Data

  val cartTimerName: String = "CartTimer"
  val checkoutTimerName: String = "CheckoutTimer"
  val paymentTimerName: String = "PaymentTimer"

  //  val log = Logging(context.system, this)
}