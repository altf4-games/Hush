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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Local DNS-only VPN. Only the fake DNS address is routed into the tunnel, so all other traffic
 * bypasses it. One thread blocks on the tun read; blocked names are answered in place with
 * NXDOMAIN, cached answers are replied to in place, and everything else is forwarded to the
 * upstream resolver over TLS by a small fixed pool. No timers, no polling.
 *
 * Domain names are never logged or stored.
 */
class DnsVpnService : VpnService() {
    @Volatile private var filter: DomainFilter = DomainFilter.EMPTY
    @Volatile private var running = false

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var pool: ThreadPoolExecutor? = null

    /**
     * Rebuilds rules and swaps resolvers off the main thread: reading the lists is disk work, and
     * closing TLS sockets sends data, which Android refuses on the main thread. One thread keeps
     * changes in order.
     */
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "hush-rules") }
    @Volatile private var resolver: DotResolver? = null
    @Volatile private var provider: DnsProvider = DnsProvider.DEFAULT
    private var versionHandle: AutoCloseable? = null

    private val cache = DnsCache()
    private val tunLock = Any()
    private val workerBuffer = ThreadLocal.withInitial { ByteArray(MAX_PACKET) }
    private val workerScratch = ThreadLocal.withInitial { ByteArray(SCRATCH) }

    private class Forward(
        val srcIp: Int,
        val srcPort: Int,
        val dstIp: Int,
        val dstPort: Int,
        val cacheKey: Long,
        /** The query's DNS message and the offset just past its question within it. */
        val payload: ByteArray,
        val questionEnd: Int,
        /** Lowercase dotted name, kept only in memory for the lifetime of this lookup. */
        val domain: ByteArray,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(applicationContext).protectionWanted = false
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
        Prefs(applicationContext).protectionWanted = false // the person turned the VPN off elsewhere
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        background.shutdown() // lets the queued socket closes finish, then the thread ends
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
        prefs.protectionWanted = true
        Stats.attach(prefs)
        // Until a first download, only a tiny starter list is in force: fetch the real ones now.
        if (prefs.listsUpdatedAt == 0L) ListUpdater.runAsync(applicationContext)
        filter = Rules.build(applicationContext, prefs)
        // Settings and list updates bump a version counter; rebuild only when it changes. Cached
        // replies were vetted under the old rules, so they are dropped too.
        versionHandle = prefs.registerVersionListener {
            background.execute {
                if (!running) return@execute
                filter = Rules.build(applicationContext, prefs)
                switchProvider(prefs.dnsProvider)
                cache.clear()
            }
        }
        // The tunnel restarts after reboot with Always-on VPN, which re-arms the weekly update.
        UpdateScheduler.sync(applicationContext, prefs.weeklyUpdate)

        provider = prefs.dnsProvider
        resolver = newResolver(provider)
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

    private fun newResolver(p: DnsProvider): DotResolver {
        val connector = TlsConnector(protect = { socket -> protect(socket) })
        return DotResolver(p.servers, open = connector::open)
    }

    /** Points lookups at another upstream. In-flight lookups finish (or fail) on the old one. */
    private fun switchProvider(next: DnsProvider) {
        if (!running || next == provider) return
        provider = next
        val old = resolver
        resolver = newResolver(next)
        old?.close() // already on the background thread
    }

    private fun shutdown() {
        running = false
        Stats.flush()
        versionHandle?.close()
        versionHandle = null
        reader?.interrupt()
        reader = null
        pool?.shutdownNow()
        pool = null
        resolver?.let { old -> background.execute { old.close() } }
        resolver = null
        cache.clear()
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

        if (filter.isBlocked(q.domain, q.domainLen)) {
            Stats.record(blocked = true)
            writeTun(output, out, DnsPacket.buildBlockedPacket(buf, q, out))
            return
        }

        // Answer from the cache without leaving the reader thread. Cached replies already passed
        // every check below, and the cache is cleared whenever the rules change.
        val key = cache.key(buf, q.dnsOffset, q.dnsLength)
        val cachedLen = cache.get(key, buf[q.dnsOffset], buf[q.dnsOffset + 1], out, DnsPacket.HEADERS_LEN)
        if (cachedLen > 0) {
            Stats.record(blocked = false)
            val total = DnsPacket.finishPacket(out, q.dstIp, q.dstPort, q.srcIp, q.srcPort, cachedLen)
            writeTun(output, out, total)
            return
        }

        val job = Forward(
            srcIp = q.srcIp,
            srcPort = q.srcPort,
            dstIp = q.dstIp,
            dstPort = q.dstPort,
            cacheKey = key,
            payload = buf.copyOfRange(q.dnsOffset, q.dnsOffset + q.dnsLength),
            questionEnd = q.questionEnd - q.dnsOffset,
            domain = q.domain.copyOf(q.domainLen),
        )
        pool?.execute { forward(job, output) }
    }

    /** Runs on a worker: pattern check, encrypted lookup, reply checks, cache, answer. */
    private fun forward(job: Forward, output: FileOutputStream) {
        try {
            val rules = filter // one consistent snapshot for this lookup
            val out = workerBuffer.get()!!

            if (rules.isRegexBlocked(job.domain, job.domain.size)) {
                Stats.record(blocked = true)
                replyError(job, out, output, DnsPacket.RCODE_NXDOMAIN)
                return
            }

            val dot = resolver ?: return
            val replyLen = dot.resolve(job.payload, job.payload.size, out, DnsPacket.HEADERS_LEN)
            if (replyLen <= 0) {
                Stats.record(blocked = false)
                // Fail closed: nothing is ever sent in cleartext. Answer SERVFAIL right away so
                // apps fail fast instead of waiting out their own timeout.
                replyError(job, out, output, DnsPacket.RCODE_SERVFAIL)
                return
            }

            if (!rules.isAllowed(job.domain, job.domain.size) &&
                ResponseGuard.shouldBlock(out, DnsPacket.HEADERS_LEN, replyLen, rules, workerScratch.get()!!)
            ) {
                Stats.record(blocked = true)
                replyError(job, out, output, DnsPacket.RCODE_NXDOMAIN)
                return
            }
            Stats.record(blocked = false)

            cache.put(job.cacheKey, out, DnsPacket.HEADERS_LEN, replyLen)
            val total = DnsPacket.finishPacket(
                out, job.dstIp, job.dstPort, job.srcIp, job.srcPort, replyLen,
            )
            writeTun(output, out, total)
        } catch (e: RuntimeException) {
            // Drop this query; the resolver on the device will retry.
        }
    }

    private fun replyError(job: Forward, out: ByteArray, output: FileOutputStream, rcode: Int) {
        val dnsLen = DnsPacket.writeErrorReply(
            job.payload, 0, job.questionEnd, out, DnsPacket.HEADERS_LEN, rcode,
        )
        if (dnsLen == 0) return
        val total = DnsPacket.finishPacket(out, job.dstIp, job.dstPort, job.srcIp, job.srcPort, dnsLen)
        writeTun(output, out, total)
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
        private const val SCRATCH = 256
        private const val WORKERS = 4
        private const val QUEUE = 256
    }
}
