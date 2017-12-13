package app

import akka.actor.Status.Failure
import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, SupervisorStrategy, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import app.Checkout.{PaymentNotReceived, PaymentReceived, PaymentServiceStarted}
import app.Common.become_

import scala.concurrent.duration.Duration

/**
  * Created by Wojciech Baczyński on 29.10.17.
  */

object PaymentService {
  final case class DoPayment(target: PaymentTarget)
  final case object PaymentConfirmed
  final case object PaymentRefused
  final case object SendConfirmation
  final case object SendRefusal

  trait PaymentTarget
  final case object Visa extends PaymentTarget
  final case object PayU extends PaymentTarget
  final case object PayPal extends PaymentTarget

  class ConnectionRefusedException extends Exception("ConnectionRefused")
  class BadRequestException extends Exception("BadRequest")
}

class PaymentService(customerActor: ActorRef) extends Actor with Timers {

  import PaymentService._

  // For .pipeTo
  import akka.pattern.pipe
  import context.dispatcher

  private var checkoutActor: Option[ActorRef] = None

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(
    loggingEnabled = false, maxNrOfRetries = 3, withinTimeRange = Duration(60, "seconds")) {
    case _: BadRequestException =>
      println("\033[31m--- Bad request while accessing to payment server\033[0m")
      Resume
    case _: ConnectionRefusedException =>
      println("\033[31m--- Connection to payment server refused. RESTARTING\033[0m")
      customerActor ! PaymentRefused
      checkoutActor.get ! PaymentNotReceived
      Restart
    case _: Exception =>
      println("\033[31m--- Unexpected failure\033[0m")
      customerActor ! PaymentRefused
      checkoutActor.get ! PaymentNotReceived
      Stop
  }

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  def receive: Receive = idle

  def idle: Receive = {
    case PaymentServiceStarted(checkoutActor_) =>
      this.checkoutActor = Option(checkoutActor_)

    case DoPayment(Visa) =>
      become_(context, waitingForPayment, "Idle", "WaitingForPayment")
      http.singleRequest(HttpRequest(uri = "http://localhost:8081/payment_request")).pipeTo(self)

    case DoPayment(PayU) =>
      become_(context, waitingForPayment, "Idle", "WaitingForPayment")
      http.singleRequest(HttpRequest(uri = "http://localhost:8082/hello")).pipeTo(self)

    case DoPayment(PayPal) =>
      become_(context, waitingForPayment, "Idle", "WaitingForPayment")
      http.singleRequest(HttpRequest(uri = "http://localhost:8083/payment_request")).pipeTo(self)
  }

  def waitingForPayment: PartialFunction[Any, Unit] = {
    case response@HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        println("\033[35m/HTTP/ Got response, body: " + body.utf8String + "\033[0m")
        response.discardEntityBytes()
      }
      become_(context, afterPayment, "WaitingForPayment", "AfterPayment")
      self ! SendConfirmation

    case response@HttpResponse(code, _, _, _) =>
      println("\033[35m/HTTP/ Request failed, response code: " + code + "\033[0m")
      response.discardEntityBytes()
      throw new BadRequestException

    case Failure(_) =>
      println("\033[35m/HTTP/ Connection refused exception\033[0m")
      throw new ConnectionRefusedException

    case _ =>
      throw new Exception
  }

  def afterPayment: Receive = {
    case SendConfirmation =>
      customerActor ! PaymentConfirmed
      checkoutActor.get ! PaymentReceived
      become_(context, idle, "AfterPayment", "Idle")
  }
}

