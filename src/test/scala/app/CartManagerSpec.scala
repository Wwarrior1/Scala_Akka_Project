package app

import java.net.URI

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import app.CartManager._
import app.Common.ActionCouldNotBeInvoked
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 29.10.17.
  */

class CartManagerSpec extends TestKit(ActorSystem("CartManagerSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  private val uri_1 = new URI("12345")
  private val uri_2 = new URI("23456")
  private val uri_3 = new URI("34567")
  private val uri_4 = new URI("45678")
  private val uri_5 = new URI("aaa")
  private val uri_6 = new URI("bbb")
  private val uri_7 = new URI("ccc")

  private val item_1 = Item(uri_1, "milk", "shop", 3, 2)
  private val item_2 = Item(uri_2, "apple", "shop", 0.5, 10)
  private val item_3 = Item(uri_3, "bread", "shop", 2.5, 1)
  private val item_4 = Item(uri_4, "banana", "shop", 1.5, 3)
  private val item_5 = Item(uri_5, "a", "shop", 123, 3)
  private val item_6 = Item(uri_6, "b", "shop", 234, 4)
  private val item_7 = Item(uri_7, "c", "shop", 234, 5)

  override def afterAll(): Unit = {
    system.terminate
  }

  "CartManager (synch)" must {
    val clientActor = TestActorRef[Client]
    val customerActor = TestActorRef(new Customer(clientActor))

    "Increment value" in {
      val cartActor = TestActorRef(new CartManager(customerActor))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemAdd(item_2)
      cartActor ! ItemAdd(item_3)
      cartActor ! ItemAdd(item_4)
      assert(cartActor.underlyingActor.getListOfItems.size == 4)
      assert(cartActor.underlyingActor.getListOfItems ==
        Map(uri_1 -> item_1, uri_2 -> item_2, uri_3 -> item_3, uri_4 -> item_4))
    }
    "Decrement value" in {
      val cartActor = TestActorRef(new CartManager(customerActor))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemAdd(item_2)
      cartActor ! ItemAdd(item_3)
      cartActor ! ItemAdd(item_4)
      cartActor ! ItemRemove(item_3, 1)
      assert(cartActor.underlyingActor.getListOfItems.size == 3)
      assert(cartActor.underlyingActor.getListOfItems ==
        Map(uri_1 -> item_1, uri_2 -> item_2, uri_4 -> item_4))
    }
    "inc and dec multiple values" in {
      val cartActor = TestActorRef(new CartManager(customerActor))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemRemove(item_1, 2)
      assert(cartActor.underlyingActor.getListOfItems.isEmpty)
      cartActor ! ItemAdd(item_2)
      cartActor ! ItemAdd(item_4)
      cartActor ! ItemAdd(item_5)
      cartActor ! ItemRemove(item_2, 10)
      cartActor ! ItemAdd(item_3)
      cartActor ! ItemAdd(item_6)
      cartActor ! ItemAdd(item_7)
      cartActor ! ItemRemove(item_3, 1)
      assert(cartActor.underlyingActor.getListOfItems
        == Map(uri_4 -> item_4, uri_5 -> item_5, uri_6 -> item_6, uri_7 -> item_7))
    }
  }

  "CartManager (asynch)" must {
    "Receive CartIsEmpty" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemRemove(item_1, 2)
      expectMsg(CartIsEmpty)
    }

    "Receive ActionCouldNotBeInvoked" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemRemove(item_2, 10)
      expectMsg(ActionCouldNotBeInvoked("Tried to remove non existing element"))
    }

    "Receive CheckoutStarted" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      cartActor ! ItemAdd(item_2)
      cartActor ! ItemAdd(item_3)
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 3) => ()
      }
    }

    "Receive CheckoutCancelled" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 1) => ()
      }
      cartActor ! CheckoutCancel
      expectMsgAnyOf(CheckoutCancelled)
    }

    "Receive CheckoutClosed" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      cartActor ! CheckoutStart
      expectMsgPF() {
        case CheckoutStarted(_, 1) => ()
      }
      cartActor ! CheckoutClose
      expectMsg(CheckoutClosed)
    }

    "Expect CartTimer expiration" in {
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
      expectMsg(5.5.second, CartIsEmpty)
    }

    "Expect CartTimer expiration after being in Checkout" in {
      val oldExpirationTime = Common.expirationTime

      Common.expirationTime = 1.second
      val cartActor = system.actorOf(Props(new CartManager(self)))
      cartActor ! ItemAdd(item_1)
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

