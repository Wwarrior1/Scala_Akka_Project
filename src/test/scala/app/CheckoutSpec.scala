package app

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import app.Cart._
import app.Checkout.{DeliverySelect, PaymentReceived, PaymentSelect, PaymentServiceStarted}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Created by Wojciech Baczyński on 30.10.17.
  */
class CheckoutSpec extends TestKit(ActorSystem("CheckoutSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Checkout" must {
    "Test parent-child using TestActorRef" in {
      println("-------- " + Common.expirationTime)
      val cartActor = TestActorRef(new Cart(self))
      var checkoutActor: Option[ActorRef] = None

      cartActor ! ItemAdd("abc")
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(checkoutActor_, 1) =>
          checkoutActor = Option(checkoutActor_)
      }

      checkoutActor.get ! DeliverySelect
      checkoutActor.get ! PaymentSelect
      expectMsgPF() {
        case PaymentServiceStarted(_) => ()
      }
      checkoutActor.get ! PaymentReceived
      expectMsg(CheckoutClosed)
    }
    "Test parent-child using TestProbe" in {
      val proxy = TestProbe()
      val parent = system.actorOf(Props(new Actor {
        val child: ActorRef = context.actorOf(Props(new Checkout(self)), "child")

        def receive: Receive = {
          case x if sender == child => proxy.ref forward x
          case x => child forward x
        }
      }))

      proxy.send(parent, CheckoutStarted(parent, 1))
      proxy.send(parent, DeliverySelect)
      proxy.send(parent, PaymentSelect)
      proxy.expectMsgPF() {
        case PaymentServiceStarted(_) => ()
      }
      proxy.send(parent, PaymentReceived)
      proxy.expectMsg(CheckoutClose)
    }
  }

  //  "Checkout" should {
  //    "Test parent-child relationship 2" in {
  //      val parent = TestProbe()
  //      val child = parent.childActorOf(Props(new Checkout(self)))
  //      parent.send(child, CheckoutStarted(parent.ref, 1))
  //      parent.send(child, DeliverySelect)
  //      parent.send(child, PaymentSelect)
  //      parent.expectMsgPF() {
  //        case PaymentServiceStarted(_) => ()
  //      }
  //      parent.send(child, PaymentReceived)
  //      parent.expectMsg(CheckoutClose)
  //    }
  //  }

}
