package com.altf4.hush.dns

import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the framing, reuse, failover and fail-closed behaviour of [DotResolver] against a local
 * server. Plain sockets stand in for TLS here; certificate checking lives in [TlsConnector].
 */
class DotResolverTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val listener = ServerSocket(0, 50, loopback)
    private val live = DotServer(loopback, listener.localPort, "test.invalid")
    private val accepted = AtomicInteger(0)
    private var created: DotResolver? = null

    @After fun tearDown() {
        created?.close()
        listener.close()
    }

    private fun resolver(
        servers: List<DotServer> = listOf(live),
        timeoutMs: Int = 2000,
    ): DotResolver {
        val r = DotResolver(servers, open = { s -> Socket(s.address, s.port) }, timeoutMs = timeoutMs)
        created = r
        return r
    }

    private fun deadServer(): DotServer {
        val s = ServerSocket(0, 1, loopback)
        val port = s.localPort
        s.close() // nothing listens here any more: connections are refused
        return DotServer(loopback, port, "dead.invalid")
    }

    /** A well-formed reply to [query]: same id, QR set. */
    private fun answerTo(query: ByteArray): ByteArray {
        val r = query.copyOf(query.size + 4)
        r[2] = (r[2].toInt() or 0x80).toByte()
        return r
    }

    /**
     * Serves length-prefixed DNS messages. [onMessage] gets each query and the index of the
     * connection it came on and returns the reply, or null to hang up. After
     * [repliesPerConnection] replies the server closes that connection.
     */
    private fun serve(
        repliesPerConnection: Int = Int.MAX_VALUE,
        onMessage: (ByteArray, Int) -> ByteArray?,
    ) {
        thread(isDaemon = true) {
            try {
                while (true) {
                    val socket = listener.accept()
                    val index = accepted.getAndIncrement()
                    thread(isDaemon = true) { handle(socket, index, repliesPerConnection, onMessage) }
                }
            } catch (e: IOException) {
                // Listener closed by the test.
            }
        }
    }

    private fun handle(
        socket: Socket,
        index: Int,
        limit: Int,
        onMessage: (ByteArray, Int) -> ByteArray?,
    ) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            var replies = 0
            while (replies < limit) {
                val length = input.readUnsignedShort()
                val query = ByteArray(length)
                input.readFully(query)
                val reply = onMessage(query, index) ?: break
                output.write(byteArrayOf((reply.size shr 8).toByte(), reply.size.toByte()))
                output.write(reply)
                output.flush()
                replies++
            }
        } catch (e: IOException) {
            // Client went away.
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                // Already closed.
            }
        }
    }

    @Test fun roundTripUsesLengthPrefixAndPlacesTheReplyAtTheOffset() {
        serve { query, _ -> answerTo(query) }
        val query = TestDns.query("example.com", id = 0xABCD)
        val out = ByteArray(200)
        val n = resolver().resolve(query, query.size, out, 28)

        assertEquals(query.size + 4, n)
        assertEquals(0xAB, out[28].toInt() and 0xFF)
        assertEquals(0xCD, out[29].toInt() and 0xFF)
        assertTrue((out[30].toInt() and 0x80) != 0) // response bit
        assertEquals(query.copyOfRange(3, query.size).toList(), out.copyOfRange(31, 28 + query.size).toList())
    }

    @Test fun theConnectionIsReusedForLaterQueries() {
        serve { query, _ -> answerTo(query) }
        val r = resolver()
        val query = TestDns.query("example.com")
        val out = ByteArray(200)
        assertTrue(r.resolve(query, query.size, out, 0) > 0)
        assertTrue(r.resolve(TestDns.query("example.org", id = 7), 29, out, 0) > 0)
        assertEquals(1, accepted.get())
    }

    @Test fun aStaleConnectionIsReplacedTransparently() {
        // The server hangs up after each reply, like an idle timeout would.
        serve(repliesPerConnection = 1) { query, _ -> answerTo(query) }
        val r = resolver()
        val query = TestDns.query("example.com")
        val out = ByteArray(200)
        assertTrue(r.resolve(query, query.size, out, 0) > 0)
        assertTrue(r.resolve(query, query.size, out, 0) > 0)
        assertEquals(2, accepted.get())
    }

    @Test fun aServerThatHangsUpWithoutAnswerFailsTheLookup() {
        serve { _, _ -> null }
        val query = TestDns.query("example.com")
        assertEquals(-1, resolver().resolve(query, query.size, ByteArray(200), 0))
    }

    @Test fun repliesWithTheWrongTransactionIdAreRejected() {
        serve { query, _ ->
            val r = answerTo(query)
            r[0] = (r[0].toInt() xor 0xFF).toByte()
            r
        }
        val query = TestDns.query("example.com")
        assertEquals(-1, resolver().resolve(query, query.size, ByteArray(200), 0))
    }

    @Test fun messagesThatAreNotResponsesAreRejected() {
        serve { query, _ -> query.copyOf() } // echoes the query: QR bit still clear
        val query = TestDns.query("example.com")
        assertEquals(-1, resolver().resolve(query, query.size, ByteArray(200), 0))
    }

    @Test fun aReplyTooBigForTheBufferIsRejected() {
        serve { query, _ -> answerTo(query) }
        val query = TestDns.query("example.com")
        val tiny = ByteArray(query.size + 2) // reply is query.size + 4
        assertEquals(-1, resolver().resolve(query, query.size, tiny, 0))
    }

    @Test fun aSilentServerTimesOut() {
        serve { query, _ ->
            Thread.sleep(1500)
            answerTo(query)
        }
        val query = TestDns.query("example.com")
        val start = System.nanoTime()
        val n = resolver(timeoutMs = 300).resolve(query, query.size, ByteArray(200), 0)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(-1, n)
        assertTrue("took ${elapsedMs}ms", elapsedMs < 1400)
    }

    @Test fun fallsOverToTheSecondServer() {
        serve { query, _ -> answerTo(query) }
        val query = TestDns.query("example.com")
        val n = resolver(servers = listOf(deadServer(), live)).resolve(query, query.size, ByteArray(200), 0)
        assertTrue(n > 0)
    }

    @Test fun failsClosedWhenNoServerIsReachable() {
        val query = TestDns.query("example.com")
        val r = resolver(servers = listOf(deadServer(), deadServer()))
        assertEquals(-1, r.resolve(query, query.size, ByteArray(200), 0))
    }

    @Test fun invalidArgumentsAreRefusedWithoutConnecting() {
        serve { query, _ -> answerTo(query) }
        val r = resolver()
        assertEquals(-1, r.resolve(ByteArray(5), 5, ByteArray(100), 0))
        val query = TestDns.query("example.com")
        assertEquals(-1, r.resolve(query, query.size + 1, ByteArray(100), 0))
        assertEquals(-1, r.resolve(query, query.size, ByteArray(100), 100))
        assertEquals(0, accepted.get())
    }
}
