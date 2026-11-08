package com.altf4.hush.dns

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.Rules
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Local DNS-only VPN. Only the fake DNS address is routed into the tunnel, so all other traffic
 * bypasses it. One thread blocks on the tun read; blocked names are answered in place with
 * NXDOMAIN, everything else is forwarded by a small fixed pool. No timers, no polling.
 *
 * Domain names are never logged or stored.
 */
class DnsVpnService : VpnService() {
    @Volatile private var blocklist: Blocklist = Blocklist.EMPTY
    @Volatile private var running = false

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var pool: ThreadPoolExecutor? = null
    private var upstream: Upstream? = null
    private var versionHandle: AutoCloseable? = null

    private val tunLock = Any()
    private val workerBuffer = ThreadLocal.withInitial { ByteArray(MAX_PACKET) }

    private class Forward(
        val srcIp: Int,
        val srcPort: Int,
        val dstIp: Int,
        val dstPort: Int,
        val payload: ByteArray,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running && !start()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun start(): Boolean {
        val pfd: ParcelFileDescriptor? = try {
            Builder()
                .setSession("Hush")
                .setMtu(MTU)
                .addAddress(CLIENT_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32) // the only route; do not add others
                .setBlocking(true)
                .establish()
        } catch (e: Exception) {
            null
        }
        if (pfd == null) return false

        val prefs = Prefs(applicationContext)
        blocklist = Rules.build(prefs)
        // Settings changes bump a version counter; rebuild only when it changes.
        versionHandle = prefs.registerVersionListener { blocklist = Rules.build(prefs) }

        upstream = Upstream(protect = { socket -> protect(socket) })
        pool = ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(QUEUE),
            ThreadPoolExecutor.DiscardPolicy(), // under overload, drop rather than pile up
        )
        tun = pfd
        running = true

        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        reader = Thread({ runLoop(input, output) }, "hush-tun").also { it.start() }
        VpnState.update(true)
        return true
    }

    private fun shutdown() {
        running = false
        versionHandle?.close()
        versionHandle = null
        reader?.interrupt()
        reader = null
        pool?.shutdownNow()
        pool = null
        upstream?.close()
        upstream = null
        try {
            tun?.close() // also unblocks the reader's read()
        } catch (e: IOException) {
            // Nothing useful to do.
        }
        tun = null
        VpnState.update(false)
    }

    private fun runLoop(input: FileInputStream, output: FileOutputStream) {
        val buf = ByteArray(MAX_PACKET)
        val out = ByteArray(MAX_PACKET)
        val query = DnsPacket.Query()
        while (running) {
            val n = try {
                input.read(buf)
            } catch (e: IOException) {
                -1
            }
            if (n < 0) break
            if (n == 0) continue
            try {
                handle(buf, n, query, out, output)
            } catch (e: RuntimeException) {
                // Malformed or unexpected packet: drop it, never crash the service.
            }
        }
        if (running) stopSelf() // the tunnel died underneath us
    }

    private fun handle(
        buf: ByteArray,
        n: Int,
        q: DnsPacket.Query,
        out: ByteArray,
        output: FileOutputStream,
    ) {
        if (!DnsPacket.parseQuery(buf, n, q)) return
        if (q.dstIp != DnsPacket.DNS_SERVER_IP || q.dstPort != DnsPacket.DNS_PORT) return

        if (blocklist.isBlocked(q.domain, q.domainLen)) {
            writeTun(output, out, DnsPacket.buildBlockedPacket(buf, q, out))
            return
        }

        val job = Forward(
            q.srcIp, q.srcPort, q.dstIp, q.dstPort,
            buf.copyOfRange(q.dnsOffset, q.dnsOffset + q.dnsLength),
        )
        pool?.execute { forward(job, output) }
    }

    private fun forward(job: Forward, output: FileOutputStream) {
        try {
            val up = upstream ?: return
            val out = workerBuffer.get()!!
            val replyLen = up.resolve(job.payload, job.payload.size, out, DnsPacket.HEADERS_LEN)
            if (replyLen <= 0) return
            val total = DnsPacket.finishPacket(
                out, job.dstIp, job.dstPort, job.srcIp, job.srcPort, replyLen,
            )
            writeTun(output, out, total)
        } catch (e: RuntimeException) {
            // Drop this query; the resolver on the device will retry.
        }
    }

    private fun writeTun(output: FileOutputStream, buf: ByteArray, len: Int) {
        if (len <= 0) return
        try {
            synchronized(tunLock) { output.write(buf, 0, len) }
        } catch (e: IOException) {
            // Tunnel closing; the read loop will end.
        }
    }

    companion object {
        const val ACTION_STOP = "com.altf4.hush.STOP"

        private const val DNS_ADDRESS = "10.111.222.2"
        private const val CLIENT_ADDRESS = "10.111.222.1"

        // Only DNS crosses this interface, so a large MTU lets big UDP replies fit in one packet.
        private const val MTU = 4096
        private const val MAX_PACKET = MTU + 64
        private const val WORKERS = 4
        private const val QUEUE = 256
    }
}
