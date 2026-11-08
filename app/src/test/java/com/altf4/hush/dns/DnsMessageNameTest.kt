package com.altf4.hush.dns

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsMessageNameTest {
    private val name = "Example.COM"

    private fun nameAt(msg: ByteArray, msgOff: Int, pos: Int, out: ByteArray = ByteArray(255)): String? {
        val n = DnsMessage.readName(msg, msgOff, msgOff + (msg.size - msgOff), pos, out)
        return if (n < 0) null else String(out, 0, n, Charsets.US_ASCII)
    }

    @Test fun plainNamesAreReadLowercase() {
        val msg = TestDns.response(name)
        assertEquals("example.com", nameAt(msg, 0, 12))
    }

    @Test fun compressionPointersAreFollowed() {
        val msg = TestDns.response(name)
        val answerStart = 12 + TestDns.encodedNameLength(name) + 4
        assertEquals("example.com", nameAt(msg, 0, answerStart)) // owner is a pointer to the question
    }

    @Test fun pointersAreRelativeToTheMessageStart() {
        val msg = TestDns.response(name)
        val buf = ByteArray(28 + msg.size)
        System.arraycopy(msg, 0, buf, 28, msg.size)
        val answerStart = 28 + 12 + TestDns.encodedNameLength(name) + 4
        val out = ByteArray(255)
        val n = DnsMessage.readName(buf, 28, buf.size, answerStart, out)
        assertEquals("example.com", String(out, 0, n, Charsets.US_ASCII))
    }

    @Test fun pointerLoopsAreRejected() {
        val msg = ByteArray(14)
        msg[12] = 0xC0.toByte()
        msg[13] = 12 // points at itself
        assertEquals(null, nameAt(msg, 0, 12))
    }

    @Test fun pointerChainsThatNeverEndAreRejected() {
        val msg = ByteArray(16)
        msg[12] = 0xC0.toByte(); msg[13] = 14
        msg[14] = 0xC0.toByte(); msg[15] = 12
        assertEquals(null, nameAt(msg, 0, 12))
    }

    @Test fun pointersPastTheEndAreRejected() {
        val msg = ByteArray(14)
        msg[12] = 0xC0.toByte()
        msg[13] = 0x7F
        assertEquals(null, nameAt(msg, 0, 12))
    }

    @Test fun labelsThatRunPastTheEndAreRejected() {
        val msg = ByteArray(16)
        msg[12] = 20 // claims 20 bytes
        assertEquals(null, nameAt(msg, 0, 12))
    }

    @Test fun reservedLabelTypesAreRejected() {
        val msg = ByteArray(16)
        msg[12] = 0x80.toByte()
        assertEquals(null, nameAt(msg, 0, 12))
        msg[12] = 0x40
        assertEquals(null, nameAt(msg, 0, 12))
    }

    @Test fun namesThatDoNotFitTheOutputAreRejected() {
        val msg = TestDns.response("a".repeat(30) + ".example.com")
        assertEquals(null, nameAt(msg, 0, 12, ByteArray(10)))
    }

    @Test fun startingOutsideTheMessageIsRejected() {
        val msg = TestDns.response(name)
        assertEquals(null, nameAt(msg, 0, msg.size))
        assertEquals(-1, DnsMessage.readName(msg, 5, msg.size, 2, ByteArray(255)))
    }

    @Test fun randomMessagesNeverThrow() {
        val rnd = Random(21)
        repeat(20000) {
            val msg = ByteArray(rnd.nextInt(64))
            rnd.nextBytes(msg)
            val out = ByteArray(255)
            for (pos in 0..msg.size) {
                DnsMessage.readName(msg, 0, msg.size, pos, out)
            }
        }
    }
}
