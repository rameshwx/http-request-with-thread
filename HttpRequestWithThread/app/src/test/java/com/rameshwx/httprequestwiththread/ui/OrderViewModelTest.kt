package com.rameshwx.httprequestwiththread.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rameshwx.httprequestwiththread.data.ApiRequestException
import com.rameshwx.httprequestwiththread.data.image.ProductImageDecoder
import com.rameshwx.httprequestwiththread.data.model.DeliveryOrder
import com.rameshwx.httprequestwiththread.data.model.DeliveryRider
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import com.rameshwx.httprequestwiththread.data.model.OrderQuote
import com.rameshwx.httprequestwiththread.data.repository.GroceryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

class OrderViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val item = GroceryItem("item-42", "Green bananas", 375)
    private val rider = DeliveryRider("rider-7", "Nimal", "Bicycle", "Colombo 03")

    @Test
    fun makesCatalogAndRiderCallsBeforeQuoteAndPassesQuoteIdToOrder() {
        val repository = RecordingRepository(item, rider)
        val viewModel = OrderViewModel(repository, ProductImageDecoder { null })

        await { viewModel.state.value?.ridersLoaded == true }
        viewModel.setCustomerName("Sam Taylor")
        viewModel.setDeliveryAddress("42 Palm Grove, Colombo 3")
        viewModel.setCustomerNote("Please ring the bell")
        viewModel.setQuantity(2)
        viewModel.getQuote()
        await { viewModel.state.value?.quote != null }
        viewModel.placeOrder()
        await { viewModel.state.value?.order != null }

        assertEquals(
            listOf("catalog", "riders", "quote:item-42:2", "order:quote-99:Sam Taylor"),
            repository.calls.toList()
        )
        assertEquals("quote-99", repository.orderedQuoteId)
        assertEquals("42 Palm Grove, Colombo 3", repository.address)
    }

    @Test
    fun doesNotRetryOrderWhenTransportOutcomeIsUnknown() {
        val repository = RecordingRepository(item, rider, failOrderUnknown = true)
        val viewModel = OrderViewModel(repository, ProductImageDecoder { null })
        await { viewModel.state.value?.ridersLoaded == true }
        viewModel.setCustomerName("Sam Taylor")
        viewModel.setDeliveryAddress("42 Palm Grove, Colombo 3")
        viewModel.getQuote()
        await { viewModel.state.value?.quote != null }
        viewModel.placeOrder()
        await { viewModel.state.value?.orderOutcomeUnknown == true }

        viewModel.placeOrder()
        Thread.sleep(30)
        assertEquals(1, repository.orderCalls)
        assertFalse(viewModel.state.value!!.canGetQuote)
    }

    @Test
    fun invalidAddressShowsFieldErrorAndDoesNotCallOrderEndpoint() {
        val repository = RecordingRepository(item, rider)
        val viewModel = OrderViewModel(repository, ProductImageDecoder { null })
        await { viewModel.state.value?.ridersLoaded == true }
        viewModel.setCustomerName("Sam Taylor")
        viewModel.setDeliveryAddress("Short")
        viewModel.getQuote()
        await { viewModel.state.value?.quote != null }

        viewModel.placeOrder()

        val rejectedState = viewModel.state.value!!
        assertTrue(rejectedState.validationAttempted)
        assertEquals("Address must be at least 8 characters after trimming.", rejectedState.deliveryAddressError)
        assertEquals(0, repository.orderCalls)

        viewModel.setDeliveryAddress("123 Main St")
        assertTrue(viewModel.state.value!!.canPlaceOrder)
        viewModel.placeOrder()
        await { viewModel.state.value?.order != null }
        assertEquals("quote-99", repository.orderedQuoteId)
    }

    @Test
    fun failedProductImageKeepsCatalogAndCheckoutAvailable() {
        val itemWithImage = item.copy(imageUrl = "https://train.uxi.asia/assets/grocery-images/item-42.png")
        val repository = RecordingRepository(itemWithImage, rider, failImage = true)
        val viewModel = OrderViewModel(repository, ProductImageDecoder { null })
        await { viewModel.state.value?.ridersLoaded == true && repository.imageCalls == 1 }

        assertEquals(listOf(itemWithImage), viewModel.state.value!!.catalog)
        assertTrue(viewModel.state.value!!.canGetQuote)
        assertTrue(viewModel.state.value!!.productImages.isEmpty())
        assertEquals(null, viewModel.state.value!!.errorMessage)

        viewModel.getQuote()
        await { viewModel.state.value?.quote != null }
        assertEquals("quote-99", viewModel.state.value!!.quote!!.quoteId)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Timed out waiting for asynchronous ViewModel state", condition())
    }

    private class RecordingRepository(
        private val item: GroceryItem,
        private val rider: DeliveryRider,
        private val failOrderUnknown: Boolean = false,
        private val failImage: Boolean = false
    ) : GroceryRepository {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var orderedQuoteId: String? = null
        @Volatile var address: String? = null
        @Volatile var orderCalls = 0
        @Volatile var imageCalls = 0

        override fun getCatalog(): List<GroceryItem> { calls += "catalog"; return listOf(item) }
        override fun getProductImage(imageUrl: String): ByteArray {
            imageCalls++
            calls += "image:$imageUrl"
            if (failImage) throw ApiRequestException("Product Image", "image unavailable")
            return byteArrayOf()
        }
        override fun getAvailableRiders(): List<DeliveryRider> { calls += "riders"; return listOf(rider) }
        override fun createQuote(item: GroceryItem, quantity: Int): OrderQuote {
            calls += "quote:${item.id}:$quantity"
            return OrderQuote("quote-99", item.name, quantity, item.priceCents * quantity, null)
        }
        override fun createOrder(quoteId: String, customerName: String, address: String, note: String): DeliveryOrder {
            orderCalls++
            calls += "order:$quoteId:$customerName"
            orderedQuoteId = quoteId
            this.address = address
            if (failOrderUnknown) throw ApiRequestException("Place Order", "unknown", outcomeUnknown = true)
            return DeliveryOrder("order-12", "received", "{}")
        }
    }
}
