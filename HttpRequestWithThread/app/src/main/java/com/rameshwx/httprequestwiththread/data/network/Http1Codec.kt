package com.rameshwx.httprequestwiththread.data.network

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

data class HttpRequestData(
    val method: String,
    val target: String,
    val host: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = byteArrayOf()
)

data class HttpResponseData(
    val statusCode: Int,
    val reason: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    fun header(name: String): String? = headers[name.lowercase()]
    fun bodyText(): String = body.toString(StandardCharsets.UTF_8)
}

/** Minimal HTTP/1.1 codec supporting fixed-length, chunked, and close-delimited responses. */
object Http1Codec {
    private const val CRLF = "\r\n"
    private const val MAX_LINE_BYTES = 16 * 1024
    private const val MAX_HEADER_COUNT = 200
    private const val MAX_BODY_BYTES = 2 * 1024 * 1024

    fun writeRequest(request: HttpRequestData, output: OutputStream) {
        require(request.method.matches(Regex("[A-Z]+"))) { "Invalid HTTP method" }
        require(request.target.startsWith('/') && !request.target.contains("\r") && !request.target.contains("\n")) {
            "Invalid request target"
        }
        output.write("${request.method} ${request.target} HTTP/1.1$CRLF".toByteArray(StandardCharsets.US_ASCII))
        output.write("Host: ${request.host}$CRLF".toByteArray(StandardCharsets.US_ASCII))
        output.write("Connection: close$CRLF".toByteArray(StandardCharsets.US_ASCII))
        if (request.headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
            output.write("Accept: application/json$CRLF".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write("Accept-Encoding: identity$CRLF".toByteArray(StandardCharsets.US_ASCII))
        request.headers.forEach { (name, value) ->
            require(name.matches(Regex("[A-Za-z0-9-]+")) && !value.contains('\r') && !value.contains('\n')) {
                "Invalid HTTP header"
            }
            output.write("$name: $value$CRLF".toByteArray(StandardCharsets.US_ASCII))
        }
        if (request.body.isNotEmpty() || request.method in setOf("POST", "PUT", "PATCH")) {
            output.write("Content-Length: ${request.body.size}$CRLF".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write(CRLF.toByteArray(StandardCharsets.US_ASCII))
        output.write(request.body)
        output.flush()
    }

    fun readResponse(input: InputStream, requestMethod: String = "GET"): HttpResponseData {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input)
        var interimCount = 0
        while (true) {
            val statusLine = readLine(stream) ?: error("Connection ended before HTTP status line")
            val parts = statusLine.split(' ', limit = 3)
            require(parts.size >= 2 && parts[0].startsWith("HTTP/1.")) { "Malformed HTTP status line" }
            val status = parts[1].toIntOrNull() ?: error("Malformed HTTP status code")
            val reason = parts.getOrElse(2) { "" }
            val headers = linkedMapOf<String, String>()
            var count = 0
            while (true) {
                val line = readLine(stream) ?: error("Connection ended in HTTP headers")
                if (line.isEmpty()) break
                require(++count <= MAX_HEADER_COUNT) { "Too many HTTP headers" }
                val colon = line.indexOf(':')
                require(colon > 0) { "Malformed HTTP header" }
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers[name] = headers[name]?.let { "$it, $value" } ?: value
            }
            if (status in 100..199 && status != 101) {
                require(++interimCount <= 4) { "Too many interim HTTP responses" }
                continue
            }
            val body = when {
                requestMethod == "HEAD" || status == 204 || status == 304 || status in 100..199 -> byteArrayOf()
                headers["transfer-encoding"]?.split(',')?.any { it.trim().equals("chunked", true) } == true -> readChunked(stream)
                headers["content-length"] != null -> readFixed(stream, headers.getValue("content-length").toLong())
                else -> readToEnd(stream)
            }
            return HttpResponseData(status, reason, headers, body)
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var previous = -1
        while (bytes.size() <= MAX_LINE_BYTES) {
            val next = input.read()
            if (next == -1) return if (bytes.size() == 0) null else error("Truncated HTTP line")
            if (previous == '\r'.code && next == '\n'.code) {
                val line = bytes.toByteArray()
                return line.copyOf(line.size - 1).toString(StandardCharsets.ISO_8859_1)
            }
            bytes.write(next)
            previous = next
        }
        error("HTTP line exceeds limit")
    }

    private fun readFixed(input: InputStream, length: Long): ByteArray {
        require(length in 0..MAX_BODY_BYTES.toLong()) { "HTTP body exceeds limit or has invalid length" }
        val bytes = ByteArray(length.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read < 0) error("Truncated HTTP body")
            offset += read
        }
        return bytes
    }

    private fun readChunked(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val lengthText = (readLine(input) ?: error("Truncated chunked response")).substringBefore(';').trim()
            val length = lengthText.toLongOrNull(16) ?: error("Invalid HTTP chunk size")
            if (length == 0L) {
                var trailers = 0
                while ((readLine(input) ?: error("Truncated trailer headers")).isNotEmpty()) {
                    require(++trailers <= MAX_HEADER_COUNT) { "Too many HTTP trailer headers" }
                }
                return output.toByteArray()
            }
            require(length <= MAX_BODY_BYTES - output.size().toLong()) { "HTTP body exceeds limit" }
            var remaining = length.toInt()
            val buffer = ByteArray(minOf(8192, remaining))
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) error("Truncated HTTP chunk")
                output.write(buffer, 0, count)
                remaining -= count
            }
            require(readLine(input) == "") { "Missing chunk terminator" }
        }
    }

    private fun readToEnd(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            require(output.size() + read <= MAX_BODY_BYTES) { "HTTP body exceeds limit" }
            output.write(buffer, 0, read)
        }
    }
}
