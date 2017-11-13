package app

import java.io.File
import java.net.URI

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import app.CartManager._
import app.Common.ActionCouldNotBeInvoked
import org.iq80.leveldb.util.FileUtils
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Wojciech Baczyński on 29.10.17.
  */

class CartManagerSpec extends TestKit(ActorSystem("CartManagerSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  val storageLocations = List(
    new File(system.settings.config.getString("akka.persistence.journal.leveldb.dir")),
    new File(system.settings.config.getString("akka.persistence.snapshot-store.local.dir"))
  )

  override def beforeAll() {
    super.beforeAll()
    storageLocations foreach FileUtils.deleteRecursively
  }

  override def afterAll() {
    super.afterAll()
    system.terminate()
    storageLocations foreach FileUtils.deleteRecursively
  }

  private val uri_1 = new URI("12345")
  private val uri_2 = new URI("23456")
  private val uri_3 = new URI("34567")

  private val item_1 = Item(uri_1, "milk", 3, 2)
  private val item_2 = Item(uri_2, "apple", 0.5, 10)
  private val item_3 = Item(uri_3, "bread", 2.5, 1)

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

