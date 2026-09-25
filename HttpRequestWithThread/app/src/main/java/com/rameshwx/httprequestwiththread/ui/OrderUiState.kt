package com.rameshwx.httprequestwiththread.ui

import android.graphics.Bitmap
import com.rameshwx.httprequestwiththread.data.model.DeliveryOrder
import com.rameshwx.httprequestwiththread.data.model.DeliveryRider
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import com.rameshwx.httprequestwiththread.data.model.OrderQuote

data class LoadedProductImage(val imageUrl: String, val bitmap: Bitmap)

data class OrderUiState(
    val catalog: List<GroceryItem> = emptyList(),
    val riders: List<DeliveryRider> = emptyList(),
    val ridersLoaded: Boolean = false,
    val selectedItemId: String? = null,
    val productImages: Map<String, LoadedProductImage> = emptyMap(),
    val quantity: Int = 1,
    val quote: OrderQuote? = null,
    val customerName: String = "",
    val deliveryAddress: String = "",
    val customerNote: String = "",
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val activeStep: String = "Loading grocery catalog…",
    val errorMessage: String? = null,
    val order: DeliveryOrder? = null,
    val orderOutcomeUnknown: Boolean = false,
    val validationAttempted: Boolean = false
) {
    val selectedItem: GroceryItem? get() = catalog.firstOrNull { it.id == selectedItemId }
    val customerNameError: String? get() = if (validationAttempted) OrderFormValidation.customerNameError(customerName) else null
    val deliveryAddressError: String? get() = if (validationAttempted) OrderFormValidation.deliveryAddressError(deliveryAddress) else null
    val customerNoteError: String? get() = if (validationAttempted) OrderFormValidation.customerNoteError(customerNote) else null
    val canGetQuote: Boolean get() = !isBusy && ridersLoaded && !orderOutcomeUnknown && selectedItem != null && quantity in 1..100
    val canAttemptPlaceOrder: Boolean get() = !isBusy && quote != null && !orderOutcomeUnknown
    val canPlaceOrder: Boolean get() = canAttemptPlaceOrder &&
        OrderFormValidation.customerNameError(customerName) == null &&
        OrderFormValidation.deliveryAddressError(deliveryAddress) == null &&
        OrderFormValidation.customerNoteError(customerNote) == null
}

object OrderFormValidation {
    fun customerNameError(value: String): String? = when (val length = value.trim().length) {
        in 2..100 -> null
        else -> if (length < 2) "Enter a name with at least 2 characters." else "Name must be 100 characters or fewer."
    }

    fun deliveryAddressError(value: String): String? = when (val length = value.trim().length) {
        in 8..300 -> null
        else -> if (length < 8) "Address must be at least 8 characters after trimming." else "Address must be 300 characters or fewer."
    }

    fun customerNoteError(value: String): String? =
        if (value.length <= 300) null else "Delivery note must be 300 characters or fewer."
}
