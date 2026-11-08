package com.altf4.hush.dns

import android.content.Context
import com.altf4.hush.data.Prefs
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Downloads the enabled lists from the fixed [ListCatalog] over HTTPS, merges them into one
 * hashed file, and tells the running service by bumping the settings version. All-or-nothing: if
 * any list fails, the previous file is kept. Domain text is parsed line by line and never stored.
 *
 * This is the only code that talks to list hosts (CLAUDE.md hard rule 1).
 */
object ListUpdater {
    class Outcome(val ok: Boolean, val message: String)

    private const val MAX_BYTES = 64L * 1024 * 1024

    /** Upper bound on merged entries (before deduplication), so a hostile list cannot exhaust memory. */
    private const val MAX_ENTRIES = 8_000_000
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private val busy = AtomicBoolean(false)

    /** Runs [update] on a new thread. Safe to call from the UI. */
    fun runAsync(context: Context) {
        val app = context.applicationContext
        Thread({ update(app) }, "hush-lists").start()
    }

    /** Blocking. Call off the main thread. Only one update runs at a time. */
    fun update(context: Context): Outcome {
        if (!busy.compareAndSet(false, true)) return Outcome(false, "Already updating")
        UpdateStatus.begin()
        val outcome = try {
            doUpdate(context)
        } catch (e: Exception) {
            Outcome(false, "Update failed. Previous lists kept.")
        } finally {
            busy.set(false)
        }
        UpdateStatus.finish(outcome.message)
        return outcome
    }

    private fun doUpdate(context: Context): Outcome {
        val prefs = Prefs(context)
        val enabled = prefs.enabledLists()
        val sources = ListCatalog.all.filter { it.id in enabled }

        val buffer = HashBuffer()
        val digests = HashMap<String, String>()
        val counts = HashMap<String, Int>()
        for (source in sources) {
            val before = buffer.size
            val sha = try {
                download(source, buffer)
            } catch (e: Exception) {
                return Outcome(false, "Could not download ${source.name}. Previous lists kept.")
            }
            if (buffer.size == before) {
                return Outcome(false, "${source.name} had no usable entries. Previous lists kept.")
            }
            if (buffer.size > MAX_ENTRIES) {
                return Outcome(false, "The selected lists are too large. Previous lists kept.")
            }
            digests[source.id] = sha
            counts[source.id] = buffer.size - before
        }

        val hashes = buffer.sortedUnique()
        Blocklist.writeFile(ListStore.file(context), hashes)
        prefs.commitListUpdate(System.currentTimeMillis(), hashes.size, digests, counts)
        return Outcome(true, "Updated: ${"%,d".format(hashes.size)} domains")
    }

    /** Streams one list into [sink] and returns the SHA-256 (hex) of the downloaded bytes. */
    private fun download(source: BlockListSource, sink: HashBuffer): String {
        val connection = URL(source.url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Hush")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            if (connection.url.protocol != "https") throw IOException("left https")

            val digest = MessageDigest.getInstance("SHA-256")
            val stream = DigestInputStream(LimitedInputStream(connection.inputStream, MAX_BYTES), digest)
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                ListParser.collect(reader, source.format, sink)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    /** Fails the read once more than the allowed number of bytes has arrived. */
    private class LimitedInputStream(stream: InputStream, private var remaining: Long) :
        FilterInputStream(stream) {
        override fun read(): Int {
            val c = super.read()
            if (c >= 0 && --remaining < 0) throw IOException("list too large")
            return c
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                remaining -= n
                if (remaining < 0) throw IOException("list too large")
            }
            return n
        }
    }
}
