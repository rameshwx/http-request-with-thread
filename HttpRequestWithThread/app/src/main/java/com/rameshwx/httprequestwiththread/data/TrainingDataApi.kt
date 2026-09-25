package com.rameshwx.httprequestwiththread.data

import com.rameshwx.httprequestwiththread.data.json.JsonValue
import com.rameshwx.httprequestwiththread.data.json.MiniJson
import com.rameshwx.httprequestwiththread.data.model.DeliveryOrder
import com.rameshwx.httprequestwiththread.data.model.DeliveryRider
import com.rameshwx.httprequestwiththread.data.model.GroceryItem
import com.rameshwx.httprequestwiththread.data.model.OrderQuote
import com.rameshwx.httprequestwiththread.data.network.HttpRequestData
import com.rameshwx.httprequestwiththread.data.network.RawHttpTransport
import com.rameshwx.httprequestwiththread.data.network.RawTransportException
import java.nio.charset.StandardCharsets
import java.net.URI
import javax.inject.Inject

class ApiRequestException(
    val step: String,
    message: String,
    val httpStatus: Int? = null,
    val outcomeUnknown: Boolean = false
) : Exception(message)

class TrainingDataApi @Inject constructor(private val transport: RawHttpTransport) {
    fun getCatalog(): List<GroceryItem> {
        val response = request("GET", "/rest/v1/grocery_catalog?select=id,name,price_cents,image_url,is_active&is_active=eq.true", "Catalog")
        return response.asArray("Catalog").mapNotNull { value ->
            val row = value as? JsonValue.Obj ?: return@mapNotNull null
            val id = row.string("id") ?: return@mapNotNull null
            val name = row.string("name") ?: row.string("product_name") ?: "Grocery item"
            val price = row.number("price_cents")?.toInt() ?: row.number("price")?.toInt()?.times(100) ?: 0
            GroceryItem(
                id = id,
                name = name,
                priceCents = price,
                description = row.string("description") ?: "Fresh from local suppliers",
                imageUrl = row.string("image_url")?.takeIf(String::isNotBlank)
            )
        }.also { if (it.isEmpty()) throw ApiRequestException("Catalog", "The active catalog did not contain any usable products.") }
    }

    fun getProductImage(imageUrl: String): ByteArray {
        val uri = try {
            val parsed = URI(imageUrl)
            (if (parsed.isAbsolute) parsed else URI("https://$HOST").resolve(parsed)).normalize()
        } catch (exception: Exception) {
            throw ApiRequestException("Product Image", "The product image URL is invalid: ${exception.message}")
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.port !in listOf(-1, 443)) {
            throw ApiRequestException("Product Image", "Product images must use a valid HTTPS URL.")
        }
        val target = buildString {
            append(uri.rawPath?.takeIf(String::isNotEmpty) ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
        val response = try {
            transport.execute(
                HttpRequestData(
                    method = "GET",
                    target = target,
                    host = uri.host,
                    headers = mapOf("Accept" to "image/*")
                )
            )
        } catch (exception: RawTransportException) {
            throw ApiRequestException("Product Image", "Image request failed: ${exception.message}")
        }
        if (response.statusCode !in 200..299) {
            throw ApiRequestException("Product Image", "HTTP ${response.statusCode}: image could not be loaded.", response.statusCode)
        }
        return response.body
    }

    fun getAvailableRiders(): List<DeliveryRider> {
        val response = request("GET", "/rest/v1/delivery_riders?select=id,display_name,vehicle_type,service_area,availability&availability=eq.available", "Available Riders")
        return response.asArray("Available Riders").mapNotNull { value ->
            val row = value as? JsonValue.Obj ?: return@mapNotNull null
            DeliveryRider(
                id = row.string("id") ?: "",
                displayName = row.string("display_name") ?: "Available rider",
                vehicleType = row.string("vehicle_type") ?: "Delivery",
                serviceArea = row.string("service_area") ?: "Service area not listed"
            )
        }
    }

    fun createQuote(item: GroceryItem, quantity: Int): OrderQuote {
        val payload = MiniJson.stringify(MiniJson.obj(
            "p_item_id" to MiniJson.str(item.id),
            "p_quantity" to MiniJson.num(quantity)
        ))
        val response = request("POST", "/rest/v1/rpc/create_order_quote", "Create Quote", payload)
        val row = response.firstObject("Create Quote")
        val quoteId = row.string("quote_id") ?: throw ApiRequestException("Create Quote", "The API response did not include a quote ID.")
        val total = row.number("total_cents")?.toInt()
            ?: row.number("total_price_cents")?.toInt()
            ?: item.priceCents * quantity
        return OrderQuote(
            quoteId = quoteId,
            itemName = item.name,
            quantity = quantity,
            totalCents = total,
            expiresAt = row.string("expires_at")
        )
    }

    fun createOrder(quoteId: String, customerName: String, address: String, note: String): DeliveryOrder {
        val orderArguments = linkedMapOf<String, JsonValue>(
            "p_quote_id" to MiniJson.str(quoteId),
            "p_customer_name" to MiniJson.str(customerName),
            "p_delivery_address" to MiniJson.str(address)
        )
        if (note.isNotBlank()) orderArguments["p_customer_note"] = MiniJson.str(note)
        val payload = MiniJson.stringify(JsonValue.Obj(orderArguments))
        val response = request("POST", "/rest/v1/rpc/create_delivery_order", "Place Order", payload)
        val row = try {
            response.firstObject("Place Order")
        } catch (_: ApiRequestException) {
            throw ApiRequestException(
                step = "Place Order",
                message = "The API returned success but its order result could not be read. Check the order status before trying again.",
                httpStatus = 200,
                outcomeUnknown = true
            )
        }
        return DeliveryOrder(
            orderId = row.string("order_id") ?: row.string("id") ?: "Order accepted",
            status = row.string("status") ?: row.string("order_status") ?: "Order placed",
            rawResponse = response
        )
    }

    private fun request(method: String, path: String, step: String, body: String? = null): String {
        val headers = linkedMapOf<String, String>()
        if (body != null) {
            headers["Content-Type"] = "application/json; charset=utf-8"
            headers["x-api-key"] = API_KEY
        }
        val request = HttpRequestData(
            method = method,
            target = path,
            host = HOST,
            headers = headers,
            body = body?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
        )
        val response = try {
            transport.execute(request)
        } catch (exception: RawTransportException) {
            val uncertainOrder = method == "POST" && path.endsWith("create_delivery_order") && exception.requestMayHaveReachedServer
            throw ApiRequestException(
                step = step,
                message = if (uncertainOrder) "Network connection ended after sending the order. The result is unknown. Check before trying again." else "Network request failed: ${exception.message}",
                outcomeUnknown = uncertainOrder
            )
        }
        val responseBody = response.bodyText()
        if (response.statusCode !in 200..299) {
            val apiMessage = runCatching {
                val obj = MiniJson.parse(responseBody) as? JsonValue.Obj
                obj?.string("message") ?: obj?.string("error") ?: responseBody
            }.getOrDefault(responseBody)
            throw ApiRequestException(step, "HTTP ${response.statusCode}: ${apiMessage.take(400)}", response.statusCode)
        }
        return responseBody
    }

    private fun String.asArray(step: String): List<JsonValue> = when (val json = parseResponse(step)) {
        is JsonValue.Arr -> json.values
        else -> throw ApiRequestException(step, "The API response was not a JSON array.")
    }

    private fun String.firstObject(step: String): JsonValue.Obj {
        val first = when (val json = parseResponse(step)) {
            is JsonValue.Arr -> json.values.firstOrNull()
            is JsonValue.Obj -> json
            else -> null
        } as? JsonValue.Obj ?: throw ApiRequestException(step, "The API response did not include a result row.")
        return first
    }

    private fun String.parseResponse(step: String): JsonValue = try {
        MiniJson.parse(this)
    } catch (exception: Exception) {
        throw ApiRequestException(step, "Could not read the API response as JSON: ${exception.message}")
    }

    private companion object {
        const val HOST = "train.uxi.asia"
        const val API_KEY = "886d2dea9ac511733d4853daa30b396c72b262e2d460009039d19389019fe257"
    }
}
