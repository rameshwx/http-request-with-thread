package com.rameshwx.httprequestwiththread.data.repository

import com.rameshwx.httprequestwiththread.data.TrainingDataApi
import com.rameshwx.httprequestwiththread.data.model.DeliveryOrder
import com.rameshwx.httprequestwiththread.data.model.DeliveryRider
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import com.rameshwx.httprequestwiththread.data.model.OrderQuote
import javax.inject.Inject

interface GroceryRepository {
    fun getCatalog(): List<GroceryItem>
    fun getProductImage(imageUrl: String): ByteArray
    fun getAvailableRiders(): List<DeliveryRider>
    fun createQuote(item: GroceryItem, quantity: Int): OrderQuote
    fun createOrder(quoteId: String, customerName: String, address: String, note: String): DeliveryOrder
}

class TrainingDataRepository @Inject constructor(private val api: TrainingDataApi) : GroceryRepository {
    override fun getCatalog() = api.getCatalog()
    override fun getProductImage(imageUrl: String) = api.getProductImage(imageUrl)
    override fun getAvailableRiders() = api.getAvailableRiders()
    override fun createQuote(item: GroceryItem, quantity: Int) = api.createQuote(item, quantity)
    override fun createOrder(quoteId: String, customerName: String, address: String, note: String) =
        api.createOrder(quoteId, customerName, address, note)
}
