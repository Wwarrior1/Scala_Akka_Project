package app

import akka.actor.{Actor, ActorRef, Timers}
import app.Checkout.{PaymentReceived, PaymentServiceStarted}
import app.Common.become_

/**
  * Created by Wojciech Baczyński on 29.10.17.
  */

object PaymentService {
  final case object DoPayment
  final case object PaymentConfirmed
  final case object SendConfirmation
}

class PaymentService(customerActor: ActorRef) extends Actor with Timers {

  import PaymentService._

  private var checkoutActor: Option[ActorRef] = None

  def receive: Receive = waitingForPayment

  def waitingForPayment: Receive = {
    case PaymentServiceStarted(checkoutActor_) =>
      this.checkoutActor = Option(checkoutActor_)

    case DoPayment =>
      become_(context, paid, "WaitingForPayment", "Paid")
      self ! SendConfirmation
  }

  def paid: Receive = {
    case SendConfirmation =>
      customerActor ! PaymentConfirmed
      checkoutActor.get ! PaymentReceived
  }
}

