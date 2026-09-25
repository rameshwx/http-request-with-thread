package com.rameshwx.httprequestwiththread.data.model

data class GroceryItem(
    val id: String,
    val name: String,
    val priceCents: Int,
    val description: String = "Fresh from local suppliers",
    val imageUrl: String? = null
)

data class DeliveryRider(
    val id: String,
    val displayName: String,
    val vehicleType: String,
    val serviceArea: String
)

data class OrderQuote(
    val quoteId: String,
    val itemName: String,
    val quantity: Int,
    val totalCents: Int,
    val expiresAt: String?
)

data class DeliveryOrder(
    val orderId: String,
    val status: String,
    val rawResponse: String
)
