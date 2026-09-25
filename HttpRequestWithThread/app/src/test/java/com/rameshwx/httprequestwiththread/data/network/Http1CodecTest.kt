package com.rameshwx.httprequestwiththread.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class Http1CodecTest {
    @Test
    fun readsContentLengthResponseAndCaseInsensitiveHeaders() {
        val input = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nX-Trace: demo\r\n\r\nhello"
            .toByteArray(StandardCharsets.US_ASCII)

        val response = Http1Codec.readResponse(ByteArrayInputStream(input))

        assertEquals(200, response.statusCode)
        assertEquals("demo", response.header("x-trace"))
        assertEquals("hello", response.bodyText())
    }

    @Test
    fun readsChunkedBodyWithExtensionsAndTrailers() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" +
            "4;part=1\r\ntest\r\n3\r\ning\r\n0\r\nX-Done: yes\r\n\r\n"
        val response = Http1Codec.readResponse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.US_ASCII)))

        assertEquals("testing", response.bodyText())
    }

    @Test
    fun writesUtf8ByteLengthAndHttp11Headers() {
        val body = """{"text":"é"}""".toByteArray(StandardCharsets.UTF_8)
        val output = ByteArrayOutputStream()
        Http1Codec.writeRequest(
            HttpRequestData("POST", "/rest/v1/rpc/demo", "example.test", mapOf("Content-Type" to "application/json"), body),
            output
        )
        val encoded = output.toByteArray().toString(StandardCharsets.ISO_8859_1)

        assertTrue(encoded.startsWith("POST /rest/v1/rpc/demo HTTP/1.1\r\n"))
        assertTrue(encoded.contains("Content-Length: ${body.size}\r\n"))
        assertTrue(encoded.endsWith(body.toString(StandardCharsets.ISO_8859_1)))
    }

    @Test
    fun skipsInterimResponseBeforeFinalResponse() {
        val raw = "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 204 No Content\r\n\r\n"
        val response = Http1Codec.readResponse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.US_ASCII)))
        assertEquals(204, response.statusCode)
        assertEquals(0, response.body.size)
    }
}
