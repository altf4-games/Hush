package com.altf4.hush

import com.altf4.hush.data.BackupResult
import com.altf4.hush.data.SettingsBackup
import com.altf4.hush.data.SettingsSnapshot
import com.altf4.hush.data.SocialMode
import com.altf4.hush.data.SocialSites
import com.altf4.hush.data.StatsLog
import com.altf4.hush.data.ThemeMode
import com.altf4.hush.dns.Blocklist
import com.altf4.hush.dns.DnsCache
import com.altf4.hush.dns.DnsMessage
import com.altf4.hush.dns.DnsPacket
import com.altf4.hush.dns.DomainFilter
import com.altf4.hush.dns.ListFormat
import com.altf4.hush.dns.ListParser
import com.altf4.hush.dns.ResponseGuard
import com.altf4.hush.dns.TestDns
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Throws malformed input at everything that parses bytes or text from outside the app: packets
 * from other apps, replies from the network, list files, backup files. The only requirement is
 * that nothing throws, loops, or reads out of bounds. Seeds are fixed so a failure repeats.
 */
class FuzzTest {
    private val iterations = 20_000

    /** Copies [src] and corrupts a few bytes, sometimes truncating or extending it. */
    private fun mutate(src: ByteArray, rnd: Random): ByteArray {
        var out = src.copyOf()
        repeat(1 + rnd.nextInt(4)) {
            when (rnd.nextInt(6)) {
                0 -> if (out.isNotEmpty()) out[rnd.nextInt(out.size)] = rnd.nextInt(256).toByte()
                1 -> if (out.isNotEmpty()) out[rnd.nextInt(out.size)] = 0xC0.toByte() // compression pointer
                2 -> if (out.isNotEmpty()) out[rnd.nextInt(out.size)] = 0xFF.toByte()
                3 -> out = out.copyOf(rnd.nextInt(out.size + 1)) // truncate
                4 -> out = out + ByteArray(rnd.nextInt(8)) { rnd.nextInt(256).toByte() }
                else -> if (out.size > 1) {
                    val i = rnd.nextInt(out.size - 1)
                    out[i] = 0
                    out[i + 1] = 0
                }
            }
        }
        return out
    }

    private fun wrap(dns: ByteArray): ByteArray {
        val pkt = ByteArray(DnsPacket.HEADERS_LEN + dns.size)
        System.arraycopy(dns, 0, pkt, DnsPacket.HEADERS_LEN, dns.size)
        DnsPacket.finishPacket(pkt, DnsPacket.CLIENT_IP, 40000, DnsPacket.DNS_SERVER_IP, 53, dns.size)
        return pkt
    }

    private val names = listOf("example.com", "a.b.c.d.example.org", "x".repeat(60) + ".net", "ads.tracker.example")

    @Test fun packetsFromOtherAppsNeverBreakTheParser() {
        val rnd = Random(1)
        val query = DnsPacket.Query()
        val out = ByteArray(4160)
        val seeds = names.map { wrap(TestDns.query(it)) }
        repeat(iterations) {
            val pkt = if (rnd.nextInt(4) == 0) ByteArray(rnd.nextInt(200)).also { rnd.nextBytes(it) } else mutate(seeds[rnd.nextInt(seeds.size)], rnd)
            // The service hands over a big buffer and the length read; reproduce that shape.
            val buf = ByteArray(4160)
            System.arraycopy(pkt, 0, buf, 0, minOf(pkt.size, buf.size))
            val len = minOf(pkt.size, buf.size)
            if (DnsPacket.parseQuery(buf, len, query)) {
                assertTrue(query.domainLen in 0..DnsPacket.MAX_NAME_LEN)
                assertTrue(query.dnsOffset + query.dnsLength <= len)
                assertTrue(query.questionEnd <= query.dnsOffset + query.dnsLength)
                DnsPacket.buildBlockedPacket(buf, query, out)
            }
        }
    }

    @Test fun repliesFromTheNetworkNeverBreakTheWalkers() {
        val rnd = Random(2)
        val filter = DomainFilter(Blocklist.EMPTY, listOf(Blocklist.build(listOf("tracker.example"))))
        val scratch = ByteArray(255)
        val seeds = listOf(
            TestDns.response("example.com"),
            TestDns.response("example.com", answerTtls = listOf(30, 60, 900), authorityTtls = listOf(10), withOpt = true),
            TestDns.response("example.com", rcode = 3),
        )
        repeat(iterations) {
            val msg = if (rnd.nextInt(4) == 0) ByteArray(rnd.nextInt(120)).also { rnd.nextBytes(it) } else mutate(seeds[rnd.nextInt(seeds.size)], rnd)
            val buf = ByteArray(msg.size + 4)
            System.arraycopy(msg, 0, buf, 2, msg.size)
            DnsMessage.scanTtls(buf, 2, msg.size, -1)
            DnsMessage.scanTtls(buf, 2, msg.size, 5)
            DnsMessage.skipName(buf, 2 + 12, 2 + msg.size)
            DnsMessage.readName(buf, 2, 2 + msg.size, 2 + 12, scratch)
            ResponseGuard.shouldBlock(buf, 2, msg.size, filter, scratch)
        }
    }

    @Test fun cacheSurvivesAnyReply() {
        val rnd = Random(3)
        var now = 0L
        val cache = DnsCache(maxEntries = 8, clock = { now })
        val query = TestDns.query("example.com")
        val key = cache.key(query, 0, query.size)
        val seed = TestDns.response("example.com", withOpt = true)
        val out = ByteArray(4160)
        repeat(iterations) {
            val msg = mutate(seed, rnd)
            cache.put(key, msg, 0, msg.size)
            now += rnd.nextInt(3) * 1_000_000_000L
            cache.get(key, 1, 2, out, DnsPacket.HEADERS_LEN)
        }
    }

    @Test fun listLinesNeverThrow() {
        val rnd = Random(4)
        val alphabet = "abcXYZ019.-_*|^#! \t\u00e9\u4e2d:/@\\"
        repeat(iterations) {
            val line = String(CharArray(rnd.nextInt(300)) { alphabet[rnd.nextInt(alphabet.length)] })
            for (format in ListFormat.entries) ListParser.parseLine(line, format)
            ListParser.normalizeDomain(line)
        }
    }

    @Test fun backupFilesNeverThrowAndAreAllOrNothing() {
        val rnd = Random(5)
        val sites = SocialSites.all.map { it.id }
        val valid = SettingsBackup.encode(
            SettingsSnapshot(
                theme = ThemeMode.Dark, nightEnabled = true, nightStart = 1380, nightEnd = 360, nightDays = 127,
                strict = false, apps = setOf("com.example.app"), social = sites.associateWith { SocialMode.Off },
                custom = setOf("example.com"), allowed = setOf("bank.example"), regexBlock = setOf("^ads[0-9]+\\."),
                regexAllow = emptySet(), lists = emptySet(), uncloak = true, rebinding = true, bypass = false, weekly = true,
            ),
        ).toByteArray(Charsets.UTF_8)
        repeat(2_000) {
            val text = String(mutate(valid, rnd), Charsets.UTF_8)
            val result = SettingsBackup.decode(text)
            assertTrue(result is BackupResult.Ok || result is BackupResult.Refused)
        }
        repeat(2_000) {
            SettingsBackup.decode(String(ByteArray(rnd.nextInt(400)).also { rnd.nextBytes(it) }, Charsets.ISO_8859_1))
        }
    }

    @Test fun savedCountersNeverThrow() {
        val rnd = Random(6)
        val valid = "100|40|19000:60:20,19001:40:20".toByteArray()
        repeat(iterations) {
            StatsLog.decode(String(mutate(valid, rnd), Charsets.ISO_8859_1))
        }
    }
}
