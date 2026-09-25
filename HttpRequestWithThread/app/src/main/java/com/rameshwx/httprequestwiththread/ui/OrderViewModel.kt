package com.rameshwx.httprequestwiththread.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rameshwx.httprequestwiththread.data.ApiRequestException
import com.rameshwx.httprequestwiththread.data.image.AndroidProductImageDecoder
import com.rameshwx.httprequestwiththread.data.image.ProductImageDecoder
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import com.rameshwx.httprequestwiththread.data.repository.GroceryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val repository: GroceryRepository,
    private val imageDecoder: ProductImageDecoder = AndroidProductImageDecoder()
) : ViewModel() {
    private val mutableState = MutableLiveData(OrderUiState())
    val state: LiveData<OrderUiState> = mutableState
    private val busy = AtomicBoolean(false)
    private val stateLock = Any()
    @Volatile private var latestState = OrderUiState()

    init {
        loadInitialData()
    }

    fun selectItem(item: GroceryItem) {
        if (busy.get() || latestState.orderOutcomeUnknown) return
        update { it.copy(selectedItemId = item.id, quote = null, errorMessage = null, order = null, orderOutcomeUnknown = false) }
    }

    fun setQuantity(quantity: Int) {
        if (busy.get() || latestState.orderOutcomeUnknown) return
        val safe = quantity.coerceIn(1, 100)
        update { if (it.quantity == safe) it else it.copy(quantity = safe, quote = null, errorMessage = null, order = null, orderOutcomeUnknown = false) }
    }

    fun setCustomerName(value: String) = update { it.copy(customerName = value.take(101)) }
    fun setDeliveryAddress(value: String) = update { it.copy(deliveryAddress = value.take(301)) }
    fun setCustomerNote(value: String) = update { it.copy(customerNote = value.take(300)) }

    fun retryInitialLoad() {
        if (busy.compareAndSet(false, true)) loadInitialData()
    }

    fun getQuote() {
        if (latestState.orderOutcomeUnknown) return
        if (!busy.compareAndSet(false, true)) return
        val snapshot = latestState
        val item = snapshot.selectedItem
        if (item == null || !snapshot.ridersLoaded) {
            busy.set(false)
            update { it.copy(errorMessage = if (item == null) "Choose a product before getting a quote." else "Load the available riders successfully before getting a quote.") }
            return
        }
        update { it.copy(isBusy = true, activeStep = "Creating your quote…", errorMessage = null, quote = null, order = null, orderOutcomeUnknown = false) }
        runOnJavaThread("create-order-quote") {
            try {
                val quote = repository.createQuote(item, snapshot.quantity)
                update { it.copy(quote = quote, activeStep = "Quote ready", errorMessage = null, isBusy = false) }
            } catch (exception: Exception) {
                update { it.copy(quote = null, errorMessage = messageFor(exception), activeStep = "Quote failed", isBusy = false) }
            } finally {
                busy.set(false)
            }
        }
    }

    fun placeOrder() {
        if (latestState.orderOutcomeUnknown) return
        if (!busy.compareAndSet(false, true)) return
        val snapshot = latestState
        val quote = snapshot.quote
        if (quote == null || OrderFormValidation.customerNameError(snapshot.customerName) != null ||
            OrderFormValidation.deliveryAddressError(snapshot.deliveryAddress) != null ||
            OrderFormValidation.customerNoteError(snapshot.customerNote) != null
        ) {
            busy.set(false)
            update {
                it.copy(
                    validationAttempted = true,
                    errorMessage = if (quote == null) "Get a fresh quote before placing your order." else it.errorMessage
                )
            }
            return
        }
        update { it.copy(isBusy = true, activeStep = "Placing your order…", errorMessage = null, validationAttempted = true) }
        runOnJavaThread("place-delivery-order") {
            try {
                val order = repository.createOrder(
                    quote.quoteId,
                    snapshot.customerName.trim(),
                    snapshot.deliveryAddress.trim(),
                    snapshot.customerNote.trim()
                )
                update { it.copy(order = order, quote = null, orderOutcomeUnknown = false, activeStep = "Order confirmed", errorMessage = null, isBusy = false) }
            } catch (exception: Exception) {
                val apiException = exception as? ApiRequestException
                val expired = apiException?.httpStatus == 409
                update {
                    it.copy(
                        quote = if (expired) null else it.quote,
                        orderOutcomeUnknown = apiException?.outcomeUnknown == true,
                        errorMessage = if (expired) "This quote expired or is no longer available. Get a new quote to continue." else messageFor(exception),
                        activeStep = if (apiException?.outcomeUnknown == true) "Order outcome unknown" else "Order failed",
                        isBusy = false
                    )
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private fun loadInitialData() {
        busy.set(true)
        runOnJavaThread("load-catalog-and-riders") {
            update { it.copy(isLoading = true, isBusy = true, activeStep = "Loading grocery catalog…", errorMessage = null) }
            try {
                val catalog = repository.getCatalog()
                update {
                    it.copy(
                        catalog = catalog,
                        productImages = emptyMap(),
                        selectedItemId = it.selectedItemId?.takeIf { id -> catalog.any { item -> item.id == id } } ?: catalog.firstOrNull()?.id,
                        activeStep = "Loading available riders…"
                    )
                }
                loadProductImages(catalog)
                val riders = repository.getAvailableRiders()
                update { it.copy(riders = riders, ridersLoaded = true, isLoading = false, isBusy = false, activeStep = "Ready") }
            } catch (exception: Exception) {
                update { it.copy(isLoading = false, isBusy = false, errorMessage = messageFor(exception), activeStep = "Could not load the order screen") }
            } finally {
                busy.set(false)
            }
        }
    }

    private fun loadProductImages(catalog: List<GroceryItem>) {
        catalog.forEach { item ->
            val imageUrl = item.imageUrl?.takeIf(String::isNotBlank) ?: return@forEach
            runOnJavaThread("load-product-image-${item.id}") {
                try {
                    val image = imageDecoder.decode(repository.getProductImage(imageUrl)) ?: return@runOnJavaThread
                    update { current ->
                        val currentItem = current.catalog.firstOrNull { it.id == item.id }
                        if (currentItem?.imageUrl != imageUrl) current
                        else current.copy(productImages = current.productImages + (item.id to LoadedProductImage(imageUrl, image)))
                    }
                } catch (_: Exception) {
                    // A product image is optional; keep the card placeholder and continue checkout.
                }
            }
        }
    }

    private fun runOnJavaThread(name: String, action: () -> Unit) {
        Thread(action, name).apply { isDaemon = true }.start()
    }

    private fun messageFor(exception: Exception): String =
        (exception as? ApiRequestException)?.let { "${it.step}: ${it.message}" }
            ?: exception.message?.takeIf { it.isNotBlank() }
            ?: "Something went wrong. Please try again."

    private fun update(transform: (OrderUiState) -> OrderUiState) {
        synchronized(stateLock) {
            latestState = transform(latestState)
            mutableState.postValue(latestState)
        }
    }
}
