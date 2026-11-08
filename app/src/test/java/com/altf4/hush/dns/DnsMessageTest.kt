package com.altf4.hush.dns

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsMessageTest {
    private val name = "example.com"

    @Test fun minimumTtlAcrossAnswers() {
        val msg = TestDns.response(name, answerTtls = listOf(300, 60, 120))
        assertEquals(60, DnsMessage.scanTtls(msg, 0, msg.size, -1))
    }

    @Test fun authorityRecordsCountToo() {
        val msg = TestDns.response(name, rcode = 3, answerTtls = emptyList(), authorityTtls = listOf(45))
        assertEquals(45, DnsMessage.scanTtls(msg, 0, msg.size, -1))
    }

    @Test fun optRecordIsIgnoredAndMessageWithOnlyOptHasNoTtl() {
        val withAnswer = TestDns.response(name, answerTtls = listOf(90), withOpt = true)
        assertEquals(90, DnsMessage.scanTtls(withAnswer, 0, withAnswer.size, -1))

        val onlyOpt = TestDns.response(name, answerTtls = emptyList(), withOpt = true)
        assertEquals(-1, DnsMessage.scanTtls(onlyOpt, 0, onlyOpt.size, -1))
    }

    @Test fun capLowersLargeTtlsAndLeavesSmallOnesAlone() {
        val msg = TestDns.response(name, answerTtls = listOf(300, 60, 10))
        val min = DnsMessage.scanTtls(msg, 0, msg.size, 50)
        assertEquals(10, min)
        assertEquals(listOf(50L, 50L, 10L), TestDns.answerTtls(msg, 0, name))
    }

    @Test fun capDoesNotTouchTheOptFlagsField() {
        val msg = TestDns.response(name, answerTtls = listOf(300), withOpt = true)
        val before = msg.copyOfRange(msg.size - 6, msg.size - 2) // OPT "TTL" = extended flags
        DnsMessage.scanTtls(msg, 0, msg.size, 5)
        assertEquals(before.toList(), msg.copyOfRange(msg.size - 6, msg.size - 2).toList())
    }

    @Test fun worksAtANonZeroOffset() {
        val msg = TestDns.response(name, answerTtls = listOf(200))
        val buf = ByteArray(28 + msg.size)
        System.arraycopy(msg, 0, buf, 28, msg.size)
        assertEquals(200, DnsMessage.scanTtls(buf, 28, msg.size, 100))
        assertEquals(listOf(100L), TestDns.answerTtls(buf, 28, name))
    }

    @Test fun truncatedMessagesAreMalformedAtEveryLength() {
        val msg = TestDns.response(name, answerTtls = listOf(300, 60), authorityTtls = listOf(30), withOpt = true)
        for (n in 0 until msg.size) {
            assertEquals("prefix of $n bytes", -1, DnsMessage.scanTtls(msg.copyOf(n), 0, n, -1))
        }
    }

    @Test fun badBoundsAreRejected() {
        val msg = TestDns.response(name)
        assertEquals(-1, DnsMessage.scanTtls(msg, 0, msg.size + 1, -1))
        assertEquals(-1, DnsMessage.scanTtls(msg, -1, msg.size, -1))
        assertEquals(-1, DnsMessage.scanTtls(msg, 5, msg.size, -1))
    }

    @Test fun randomMessagesNeverThrow() {
        val rnd = Random(11)
        val base = TestDns.response(name, answerTtls = listOf(300, 60), authorityTtls = listOf(30), withOpt = true)
        repeat(20000) {
            val m = base.copyOf()
            repeat(1 + rnd.nextInt(5)) { m[rnd.nextInt(m.size)] = rnd.nextInt(256).toByte() }
            DnsMessage.scanTtls(m, 0, if (rnd.nextBoolean()) m.size else rnd.nextInt(m.size + 1), rnd.nextInt(100) - 1)

            val junk = ByteArray(rnd.nextInt(80))
            rnd.nextBytes(junk)
            DnsMessage.scanTtls(junk, 0, junk.size, -1)
        }
    }
}
