package com.altf4.hush.dns

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Forwards a DNS message to an upstream resolver over UDP and returns the reply. Each worker
 * thread keeps one socket, passed through [protect] so the VPN does not capture its own traffic.
 * Tries each server in order with a per-server timeout. No caching here.
 */
class Upstream(
    private val protect: (DatagramSocket) -> Boolean,
    private val servers: List<InetAddress> = DEFAULT_SERVERS,
    private val timeoutMs: Int = 3000,
    private val port: Int = 53,
) {
    private val local = ThreadLocal<DatagramSocket?>()
    private val open = CopyOnWriteArrayList<DatagramSocket>()

    /**
     * Sends `query[0 until queryLen]` and writes the matching reply into `out` starting at
     * [outOffset]. Returns the reply length, or -1 if no server answered in time.
     */
    fun resolve(query: ByteArray, queryLen: Int, out: ByteArray, outOffset: Int): Int {
        if (queryLen < 12 || outOffset >= out.size) return -1
        for (server in servers) {
            val socket = try {
                socketForThread()
            } catch (e: IOException) {
                return -1
            }
            try {
                socket.send(DatagramPacket(query, queryLen, server, port))
                val deadline = System.nanoTime() + timeoutMs * 1_000_000L
                val reply = DatagramPacket(out, outOffset, out.size - outOffset)
                while (true) {
                    val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
                    if (remainingMs <= 0) break
                    socket.soTimeout = remainingMs.toInt()
                    reply.setData(out, outOffset, out.size - outOffset)
                    socket.receive(reply)
                    val fromServer = reply.address == server && reply.port == port
                    val sameId = out[outOffset] == query[0] && out[outOffset + 1] == query[1]
                    if (fromServer && sameId && reply.length >= 12) return reply.length
                    // Anything else (stale or spoofed reply) is ignored; keep waiting.
                }
            } catch (e: SocketTimeoutException) {
                // Try the next server.
            } catch (e: IOException) {
                dropSocket(socket)
            }
        }
        return -1
    }

    /** Closes every socket. Safe to call more than once. */
    fun close() {
        for (s in open) s.close()
        open.clear()
    }

    private fun socketForThread(): DatagramSocket {
        local.get()?.let { return it }
        val s = DatagramSocket()
        if (!protect(s)) {
            s.close()
            throw IOException("protect failed")
        }
        open.add(s)
        local.set(s)
        return s
    }

    private fun dropSocket(s: DatagramSocket) {
        s.close()
        open.remove(s)
        if (local.get() === s) local.remove()
    }

    companion object {
        /**
         * Quad9 primary and secondary. Fixed for now; using the network's own resolver needs
         * ACCESS_NETWORK_STATE and is a pending decision (see plan.md).
         */
        val DEFAULT_SERVERS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(9, 9, 9, 9)),
            InetAddress.getByAddress(byteArrayOf(149.toByte(), 112, 112, 112)),
        )
    }
}
