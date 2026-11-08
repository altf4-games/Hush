package com.altf4.hush.dns

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Arrays

/**
 * Immutable set of domains stored as a sorted array of 64-bit hashes. Lookups walk the name's
 * labels from the left and binary-search each suffix, so listing `example.com` also matches
 * `ads.example.com`. A bare TLD is never matched. No per-query allocation, and no domain strings
 * are kept: only hashes.
 *
 * Big lists live in a file that is memory-mapped, so they cost almost no heap and load instantly.
 * File layout (big-endian): magic `HUSH`, int version, long count, then `count` sorted longs.
 */
class Blocklist private constructor(private val hashes: LongBuffer) {
    val size: Int get() = hashes.limit()

    /** Confirms a hash hit for the suffix `domain[start until end]`. Called only after a hash match. */
    fun interface SuffixCheck {
        fun confirm(domain: ByteArray, start: Int, end: Int): Boolean
    }

    /**
     * True if [domain] or any parent with at least two labels is in the set. With a [check], a
     * hash hit only counts if the check also confirms it, for lists where a hash collision crafted
     * by someone else would be harmful (the allow list).
     */
    fun matches(domain: ByteArray, len: Int, check: SuffixCheck? = null): Boolean {
        if (hashes.limit() == 0 || len <= 0) return false

        var lastDot = -1
        for (i in len - 1 downTo 0) {
            if (domain[i] == DOT) {
                lastDot = i
                break
            }
        }
        if (lastDot < 0) return false // single label: a bare TLD or hostname

        // Candidate suffixes begin at each label start up to the last dot, so every candidate has
        // at least two labels.
        var start = 0
        while (start <= lastDot) {
            if (contains(hash(domain, start, len)) && (check == null || check.confirm(domain, start, len))) return true
            var i = start
            while (domain[i] != DOT) i++
            start = i + 1
        }
        return false
    }

    private fun contains(h: Long): Boolean {
        var lo = 0
        var hi = hashes.limit() - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = hashes.get(mid)
            if (v < h) lo = mid + 1 else if (v > h) hi = mid - 1 else return true
        }
        return false
    }

    companion object {
        val EMPTY = Blocklist(LongBuffer.wrap(LongArray(0)))

        private const val DOT: Byte = 46
        private val FNV_OFFSET = 0xcbf29ce484222325uL.toLong()
        private const val FNV_PRIME = 0x100000001b3L

        private const val MAGIC = 0x48555348 // "HUSH"
        private const val VERSION = 1
        private const val HEADER = 16
        private const val WRITE_CHUNK = 8192

        /** FNV-1a 64-bit over `domain[start until end]`. */
        fun hash(domain: ByteArray, start: Int, end: Int): Long {
            var h = FNV_OFFSET
            for (i in start until end) {
                h = (h xor (domain[i].toLong() and 0xFF)) * FNV_PRIME
            }
            return h
        }

        /** Same hash for a domain given as text (ASCII). */
        fun hash(domain: String): Long {
            var h = FNV_OFFSET
            for (c in domain) {
                h = (h xor (c.code.toLong() and 0xFF)) * FNV_PRIME
            }
            return h
        }

        /** Hashes of the valid domains in [domains], sorted and deduplicated. */
        fun sortedHashes(domains: Collection<String>): LongArray {
            val tmp = LongArray(domains.size)
            var n = 0
            for (d in domains) {
                val normalized = ListParser.normalizeDomain(d) ?: continue
                tmp[n++] = hash(normalized)
            }
            if (n == 0) return LongArray(0)
            Arrays.sort(tmp, 0, n)
            var m = 1
            for (i in 1 until n) {
                if (tmp[i] != tmp[m - 1]) tmp[m++] = tmp[i]
            }
            return tmp.copyOf(m)
        }

        /** Builds a list from domain names. Invalid entries (and bare TLDs) are skipped. */
        fun build(domains: Collection<String>): Blocklist = fromSorted(sortedHashes(domains))

        /** Wraps hashes that are already sorted ascending and unique. */
        fun fromSorted(sorted: LongArray): Blocklist =
            if (sorted.isEmpty()) EMPTY else Blocklist(LongBuffer.wrap(sorted))

        /** Memory-maps a list file. Returns null if it is missing or not a valid list file. */
        fun fromFile(file: File): Blocklist? {
            if (!file.isFile) return null
            return try {
                RandomAccessFile(file, "r").use { mapList(it) }
            } catch (e: IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }

        private fun mapList(raf: RandomAccessFile): Blocklist? {
            val fileSize = raf.length()
            if (fileSize < HEADER || fileSize > Int.MAX_VALUE) return null
            val payload = fileSize - HEADER
            if (payload % 8L != 0L) return null
            // The mapping stays valid after the file is closed.
            val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
            if (map.getInt(0) != MAGIC || map.getInt(4) != VERSION) return null
            if (map.getLong(8) != payload / 8L) return null
            map.position(HEADER)
            return Blocklist(map.slice().asLongBuffer())
        }

        /**
         * Writes the first [count] entries of [sorted] (ascending, unique) to [file] atomically:
         * a temporary file is written and renamed, so readers never see a partial list.
         */
        fun writeFile(file: File, sorted: LongArray, count: Int = sorted.size) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(
                    ByteBuffer.allocate(HEADER)
                        .putInt(MAGIC).putInt(VERSION).putLong(count.toLong())
                        .array(),
                )
                val chunk = ByteBuffer.allocate(WRITE_CHUNK * 8)
                var i = 0
                while (i < count) {
                    chunk.clear()
                    val n = minOf(WRITE_CHUNK, count - i)
                    for (k in 0 until n) chunk.putLong(sorted[i + k])
                    out.write(chunk.array(), 0, n * 8)
                    i += n
                }
            }
            Files.move(
                tmp.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }
}
