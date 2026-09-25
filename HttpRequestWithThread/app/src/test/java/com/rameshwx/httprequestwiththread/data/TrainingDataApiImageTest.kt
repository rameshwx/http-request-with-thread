package com.rameshwx.httprequestwiththread.data

import com.rameshwx.httprequestwiththread.data.network.HttpRequestData
import com.rameshwx.httprequestwiththread.data.network.HttpResponseData
import com.rameshwx.httprequestwiththread.data.network.RawHttpTransport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDataApiImageTest {
    @Test
    fun catalogPreservesTwelveDistinctImageUrlsForTheirProducts() {
        val rows = (1..12).joinToString(",") { index ->
            """{"id":"item-$index","name":"Product $index","price_cents":100,"image_url":"https://train.uxi.asia/assets/grocery-images/item-$index.png","is_active":true}"""
        }
        val transport = RecordingTransport(HttpResponseData(200, "OK", emptyMap(), "[$rows]".toByteArray()))
        val items = TrainingDataApi(transport).getCatalog()

        assertEquals(12, items.size)
        assertEquals((1..12).map { "item-$it" }, items.map { it.id })
        assertEquals(
            (1..12).map { "https://train.uxi.asia/assets/grocery-images/item-$it.png" },
            items.map { it.imageUrl }
        )
        assertEquals(12, items.mapNotNull { it.imageUrl }.distinct().size)
        assertTrue(transport.requests.single().target.contains("image_url"))
    }

    @Test
    fun binaryImageResponseIsReturnedUnmodifiedWithoutApiKey() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 1, -1)
        val transport = RecordingTransport(HttpResponseData(200, "OK", mapOf("content-type" to "image/png"), pngBytes))

        val received = TrainingDataApi(transport)
            .getProductImage("https://train.uxi.asia/assets/grocery-images/one.png?size=small")

        assertArrayEquals(pngBytes, received)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("train.uxi.asia", request.host)
        assertEquals("/assets/grocery-images/one.png?size=small", request.target)
        assertEquals("image/*", request.headers["Accept"])
        assertTrue(request.headers.keys.none { it.equals("x-api-key", ignoreCase = true) })
    }

    private class RecordingTransport(private val response: HttpResponseData) : RawHttpTransport {
        val requests = mutableListOf<HttpRequestData>()

        override fun execute(request: HttpRequestData): HttpResponseData {
            requests += request
            return response
        }
    }
}
