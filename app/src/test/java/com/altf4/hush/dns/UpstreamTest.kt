package com.altf4.hush.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises [Upstream] against a throwaway UDP server on the loopback interface. */
class UpstreamTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val server = DatagramSocket(0, loopback)
    private var created: Upstream? = null
    private var protectCalls = 0

    @After fun tearDown() {
        created?.close()
        server.close()
    }

    private fun newUpstream(timeoutMs: Int = 2000): Upstream {
        val u = Upstream(
            protect = { protectCalls++; true },
            servers = listOf(loopback),
            timeoutMs = timeoutMs,
            port = server.localPort,
        )
        created = u
        return u
    }

    private fun query(id: Int) = ByteArray(20) { i ->
        when (i) {
            0 -> (id shr 8).toByte()
            1 -> id.toByte()
            else -> i.toByte()
        }
    }

    /** Runs [handler] for each datagram the server receives, on its own thread. */
    private fun serve(handler: (DatagramPacket) -> Unit) {
        Thread {
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            try {
                while (true) {
                    p.setData(buf, 0, buf.size)
                    server.receive(p)
                    handler(p)
                }
            } catch (e: Exception) {
                // Socket closed by the test.
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun reply(to: DatagramPacket, bytes: ByteArray) {
        server.send(DatagramPacket(bytes, bytes.size, to.address, to.port))
    }

    @Test fun returnsTheReplyAtTheRequestedOffset() {
        val response = ByteArray(40) { (it + 100).toByte() }
        serve { p ->
            val r = response.copyOf()
            r[0] = p.data[0]
            r[1] = p.data[1]
            reply(p, r)
        }
        val q = query(0x1234)
        val out = ByteArray(200)
        val n = newUpstream().resolve(q, q.size, out, 28)

        assertEquals(40, n)
        assertEquals(0x12, out[28].toInt())
        assertEquals(0x34, out[29].toInt())
        assertArrayEquals(response.copyOfRange(2, 40), out.copyOfRange(30, 68))
        assertTrue(protectCalls >= 1)
    }

    @Test fun ignoresRepliesWithTheWrongId() {
        serve { p ->
            reply(p, ByteArray(30) { 0x7F }) // wrong id first
            val good = ByteArray(30) { 5 }
            good[0] = p.data[0]
            good[1] = p.data[1]
            reply(p, good)
        }
        val q = query(0x0A0B)
        val out = ByteArray(100)
        val n = newUpstream().resolve(q, q.size, out, 0)

        assertEquals(30, n)
        assertEquals(0x0A, out[0].toInt())
        assertEquals(0x0B, out[1].toInt())
    }

    @Test fun timesOutWhenNobodyAnswers() {
        serve { /* never reply */ }
        val q = query(1)
        val start = System.nanoTime()
        val n = newUpstream(timeoutMs = 300).resolve(q, q.size, ByteArray(100), 0)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(-1, n)
        assertTrue("took ${elapsedMs}ms", elapsedMs in 250..2000)
    }

    @Test fun refusesTooShortQueriesWithoutSending() {
        assertEquals(-1, newUpstream().resolve(ByteArray(5), 5, ByteArray(100), 0))
    }

    @Test fun failsCleanlyWhenProtectIsDenied() {
        val u = Upstream(
            protect = { false },
            servers = listOf(loopback),
            timeoutMs = 200,
            port = server.localPort,
        )
        created = u
        val q = query(2)
        assertEquals(-1, u.resolve(q, q.size, ByteArray(100), 0))
    }
}
