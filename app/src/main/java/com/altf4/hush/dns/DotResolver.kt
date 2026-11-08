package com.altf4.hush.dns

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** A DNS-over-TLS server: connect to [address]:[port] and verify its certificate for [hostname]. */
data class DotServer(val address: InetAddress, val port: Int, val hostname: String)

/**
 * Forwards DNS messages to an upstream resolver over TLS (RFC 7858): each message is sent with a
 * two-byte length prefix on a persistent connection. There is deliberately **no plain-UDP
 * fallback**: if no server can be reached over TLS the lookup fails (fail closed), so nothing is
 * ever sent in cleartext.
 *
 * Each worker thread keeps one connection and reuses it. A connection that has gone stale (server
 * idle timeout, network change) is dropped and replaced, trying each server in order. The
 * [open] callback returns a connected, handshaken, verified socket (see [TlsConnector]); tests
 * pass plain sockets. No timers: connections are opened on demand and closed only on failure or
 * [close].
 */
class DotResolver(
    private val servers: List<DotServer>,
    private val open: (DotServer) -> Socket,
    private val timeoutMs: Int = 3000,
) {
    private class Connection(val socket: Socket) {
        val input: InputStream = BufferedInputStream(socket.getInputStream(), 4096)
        val output: OutputStream = socket.getOutputStream()
    }

    private val local = ThreadLocal<Connection?>()
    private val all = CopyOnWriteArrayList<Connection>()
    @Volatile private var closed = false

    /**
     * Sends `query[0 until queryLen]` and writes the reply into `out` starting at [outOffset].
     * Returns the reply length, or -1 if no server answered over TLS in time.
     */
    fun resolve(query: ByteArray, queryLen: Int, out: ByteArray, outOffset: Int): Int {
        if (queryLen < 12 || queryLen > MAX_MESSAGE || queryLen > query.size) return -1
        if (outOffset < 0 || outOffset >= out.size) return -1

        val frame = ByteArray(queryLen + 2)
        frame[0] = (queryLen shr 8).toByte()
        frame[1] = queryLen.toByte()
        System.arraycopy(query, 0, frame, 2, queryLen)

        val reused = local.get()
        if (reused != null) {
            val n = exchange(reused, frame, out, outOffset)
            if (n > 0) return n
            drop(reused) // stale or broken: fall through to fresh connections
        }

        for (server in servers) {
            val connection = connect(server) ?: continue
            val n = exchange(connection, frame, out, outOffset)
            if (n > 0) {
                local.set(connection)
                return n
            }
            close(connection)
        }
        return -1
    }

    /** Closes every connection. Safe to call more than once. */
    fun close() {
        closed = true
        for (c in all) {
            try {
                c.socket.close()
            } catch (e: IOException) {
                // Already gone.
            }
        }
        all.clear()
    }

    private fun connect(server: DotServer): Connection? {
        if (closed) return null // replaced by another resolver; do not leak new sockets
        var socket: Socket? = null
        return try {
            socket = open(server)
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            val c = Connection(socket)
            all.add(c)
            c
        } catch (e: IOException) {
            closeQuietly(socket)
            null
        } catch (e: RuntimeException) {
            closeQuietly(socket)
            null
        }
    }

    /** One request and its reply on [c]. Returns the reply length, or -1 on any problem. */
    private fun exchange(c: Connection, frame: ByteArray, out: ByteArray, outOffset: Int): Int {
        try {
            c.output.write(frame)
            c.output.flush()

            val hi = c.input.read()
            val lo = c.input.read()
            if (hi < 0 || lo < 0) return -1
            val length = (hi shl 8) or lo
            if (length < 12 || length > out.size - outOffset) return -1

            var got = 0
            while (got < length) {
                val n = c.input.read(out, outOffset + got, length - got)
                if (n < 0) return -1
                got += n
            }
            // Must answer this very query: same transaction id, and be a response.
            if (out[outOffset] != frame[2] || out[outOffset + 1] != frame[3]) return -1
            if ((out[outOffset + 2].toInt() and 0x80) == 0) return -1
            return length
        } catch (e: IOException) {
            return -1
        }
    }

    private fun drop(c: Connection) {
        close(c)
        if (local.get() === c) local.remove()
    }

    private fun close(c: Connection) {
        closeQuietly(c.socket)
        all.remove(c)
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Already gone.
        }
    }

    companion object {
        private const val MAX_MESSAGE = 65535
    }
}
