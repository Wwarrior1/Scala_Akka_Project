package app

import java.io.File

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import app.CartManager._
import app.Checkout.{DeliverySelect, PaymentReceived, PaymentSelect, PaymentServiceStarted}
import org.iq80.leveldb.util.FileUtils
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Created by Wojciech Baczyński on 30.10.17.
  */
class CheckoutSpec extends TestKit(ActorSystem("CheckoutSpec"))
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

  "Checkout" must {
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

}
