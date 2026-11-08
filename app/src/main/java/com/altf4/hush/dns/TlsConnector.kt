package com.altf4.hush.dns

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Opens a verified TLS connection to a [DotServer]. The TCP socket is passed through [protect]
 * before connecting so the VPN does not capture its own traffic. Security properties:
 *
 *  - TLS 1.2 or 1.3 only, with the system's trusted CAs (user-installed CAs are not trusted).
 *  - The server certificate must be valid for the server's hostname. This is checked twice: by
 *    the platform (endpoint identification) and again explicitly with the default hostname
 *    verifier, so a certificate for any other name is refused.
 *  - The server is addressed by fixed IP, so its name is never resolved through DNS.
 *
 * Throws [IOException] on any failure; the socket is closed first.
 */
class TlsConnector(
    private val protect: (Socket) -> Boolean,
    private val timeoutMs: Int = 3000,
) {
    fun open(server: DotServer): Socket {
        val plain = Socket()
        try {
            if (!protect(plain)) throw IOException("could not protect socket")
            plain.connect(InetSocketAddress(server.address, server.port), timeoutMs)
            plain.soTimeout = timeoutMs // bounds the handshake

            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, server.hostname, server.port, true) as SSLSocket

            val protocols = ssl.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }
            if (protocols.isEmpty()) throw IOException("no modern TLS version available")
            ssl.enabledProtocols = protocols.toTypedArray()

            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            ssl.sslParameters = params

            ssl.startHandshake()
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(server.hostname, ssl.session)) {
                ssl.close()
                throw SSLPeerUnverifiedException("certificate is not valid for ${server.hostname}")
            }
            return ssl
        } catch (e: IOException) {
            closeQuietly(plain)
            throw e
        } catch (e: RuntimeException) {
            closeQuietly(plain)
            throw IOException("TLS setup failed", e)
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (e: IOException) {
            // Already gone.
        }
    }
}
