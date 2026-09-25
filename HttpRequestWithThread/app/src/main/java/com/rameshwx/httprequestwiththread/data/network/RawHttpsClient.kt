package com.rameshwx.httprequestwiththread.data.network

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class RawTransportException(
    message: String,
    val requestMayHaveReachedServer: Boolean,
    cause: Throwable? = null
) : Exception(message, cause)

/** Direct TLS socket transport with endpoint verification and a small HTTP/1.1 codec. */
interface RawHttpTransport {
    fun execute(request: HttpRequestData): HttpResponseData
}

class RawHttpsClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : RawHttpTransport {
    override fun execute(request: HttpRequestData): HttpResponseData {
        var requestMayHaveReachedServer = false
        try {
            val rawSocket = Socket()
            try {
                rawSocket.connect(InetSocketAddress(request.host, 443), connectTimeoutMillis)
                rawSocket.soTimeout = readTimeoutMillis
                val tlsSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(rawSocket, request.host, 443, true) as SSLSocket
                tlsSocket.soTimeout = readTimeoutMillis
                val sslParameters = tlsSocket.sslParameters
                sslParameters.endpointIdentificationAlgorithm = "HTTPS"
                sslParameters.serverNames = listOf(SNIHostName(request.host))
                tlsSocket.sslParameters = sslParameters
                tlsSocket.use { secure ->
                    secure.startHandshake()
                    requestMayHaveReachedServer = true
                    Http1Codec.writeRequest(request, secure.outputStream)
                    return Http1Codec.readResponse(secure.inputStream, request.method)
                }
            } finally {
                runCatching { rawSocket.close() }
            }
        } catch (exception: RawTransportException) {
            throw exception
        } catch (exception: Exception) {
            throw RawTransportException(
                message = exception.message ?: exception.javaClass.simpleName,
                requestMayHaveReachedServer = requestMayHaveReachedServer,
                cause = exception
            )
        }
    }
}
