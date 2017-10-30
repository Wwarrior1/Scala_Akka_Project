package app

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import app.Cart._
import app.Common.ActionCouldNotBeInvoked
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 29.10.17.
  */

class CartSpec extends TestKit(ActorSystem("CartSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Cart (synch)" must {
    val clientActor = TestActorRef[Client]
    val customerActor = TestActorRef(new Customer(clientActor))

    "Increment value" in {
      val cartActor = TestActorRef(new Cart(customerActor))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemAdd("def")
      cartActor ! ItemAdd("ghi")
      cartActor ! ItemAdd("qwerty")
      assert(cartActor.underlyingActor.getListOfItems.length == 4)
      assert(cartActor.underlyingActor.getListOfItems == List("abc", "def", "ghi", "qwerty"))
    }
    "Decrement value" in {
      val cartActor = TestActorRef(new Cart(customerActor))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemAdd("def")
      cartActor ! ItemAdd("ghi")
      cartActor ! ItemAdd("qwerty")
      cartActor ! ItemRemove("ghi")
      assert(cartActor.underlyingActor.getListOfItems.length == 3)
      assert(cartActor.underlyingActor.getListOfItems == List("abc", "def", "qwerty"))
    }
    "inc and dec multiple values" in {
      val cartActor = TestActorRef(new Cart(customerActor))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemRemove("abc")
      cartActor ! ItemRemove("def")
      assert(cartActor.underlyingActor.getListOfItems.isEmpty)
      cartActor ! ItemAdd("qqq")
      cartActor ! ItemAdd("www")
      cartActor ! ItemAdd("eee")
      cartActor ! ItemRemove("qqq")
      cartActor ! ItemAdd("rrr")
      cartActor ! ItemAdd("ttt")
      cartActor ! ItemAdd("yyy")
      cartActor ! ItemRemove("rrr")
      assert(cartActor.underlyingActor.getListOfItems == List("www", "eee", "ttt", "yyy"))
    }
  }

  "Cart (asynch)" must {
    "Receive CartIsEmpty" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemRemove("abc")
      expectMsg(CartIsEmpty)
    }

    "Receive ActionCouldNotBeInvoked" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemRemove("def")
      expectMsg(ActionCouldNotBeInvoked("Tried to remove non existing element"))
    }

    "Receive CheckoutStarted" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! ItemAdd("def")
      cartActor ! ItemAdd("ghi")
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 3) => ()
      }
    }

    "Receive CheckoutCancelled" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 1) => ()
      }
      cartActor ! CheckoutCancel
      expectMsgAnyOf(CheckoutCancelled)
    }

    "Receive CheckoutClosed" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 1) => ()
      }
      cartActor ! CheckoutClose
      expectMsg(CheckoutClosed)
    }

    "Expect CartTimer expiration" in {
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      expectMsg(5.5.second, CartIsEmpty)
    }

    "Expect CartTimer expiration after being in Checkout" in {
      val oldExpirationTime = Common.expirationTime

      Common.expirationTime = 1.second
      val cartActor = system.actorOf(Props(new Cart(self)))
      cartActor ! ItemAdd("abc")
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 1) => ()
      }
      cartActor ! CheckoutCancel
      expectMsgAnyOf(CheckoutCancelled)
      expectMsg(1.1.second, CartIsEmpty)

      Common.expirationTime = oldExpirationTime
    }
  }

}

