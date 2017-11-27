package app

import java.net.URI

import org.scalatest.{BeforeAndAfter, WordSpecLike}

/**
  * Created by Wojciech Baczyński on 12.11.17.
  */
class CartTest extends WordSpecLike with BeforeAndAfter {

  private val uri_1 = new URI("aaa")
  private val uri_2 = new URI("bbb")
  private val uri_3 = new URI("ccc")
  private val uri_4 = new URI("ddd")

  private val item_1 = Item(uri_1, "kakao", "shop", 10, 2)
  private val item_2 = Item(uri_2, "coffe", "shop", 62, 1)
  private val item_3 = Item(uri_3, "milk", "shop", 3, 5)
  private val item_4 = Item(uri_4, "water", "shop", 1.5, 6)

  var shoppingCart: Cart = _

  before {
    shoppingCart = new Cart(
      Map(uri_1 -> item_1, uri_2 -> item_2, uri_3 -> item_3))
  }

  "Cart" must {

    "Add item" in {
      shoppingCart = shoppingCart.addItem(item_4)
      assert(shoppingCart.items ==
        Map(uri_1 -> item_1, uri_2 -> item_2,
          uri_3 -> item_3, uri_4 -> item_4))
    }

    "Remove item (whole)" in {
      shoppingCart = shoppingCart.removeItem(item_1, 2)
      assert(shoppingCart.items ==
        Map(uri_2 -> item_2, uri_3 -> item_3))
    }

    "Remove item (partially 1)" in {
      shoppingCart = shoppingCart.removeItem(item_3, 0)
      assert(shoppingCart.items ==
        Map(uri_1 -> item_1, uri_2 -> item_2, uri_3 -> item_3))
    }

    "Remove item (partially 2)" in {
      shoppingCart = shoppingCart.removeItem(item_3, 1)
      assert(shoppingCart.items ==
        Map(uri_1 -> item_1, uri_2 -> item_2, uri_3 -> item_3.copy(count = 4)))
    }

    "Remove item (partially 3)" in {
      shoppingCart = shoppingCart.removeItem(item_3, 4)
      assert(shoppingCart.items ==
        Map(uri_1 -> item_1, uri_2 -> item_2, uri_3 -> item_3.copy(count = 1)))
    }

    "Clear cart" in {
      shoppingCart = shoppingCart.clearCart()
      assert(shoppingCart.items == Map.empty)
    }

  }
}
