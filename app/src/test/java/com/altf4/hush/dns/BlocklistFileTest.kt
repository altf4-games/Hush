package com.altf4.hush.dns

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlocklistFileTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun Blocklist.blocks(name: String): Boolean {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        return matches(bytes, bytes.size)
    }

    private fun write(name: String, domains: Collection<String>): File {
        val file = File(tmp.root, name)
        Blocklist.writeFile(file, Blocklist.sortedHashes(domains))
        return file
    }

    @Test fun roundTripKeepsMatchingBehaviour() {
        val file = write("list.bin", listOf("ads.example.com", "tracker.net"))
        val loaded = Blocklist.fromFile(file)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertTrue(loaded.blocks("ads.example.com"))
        assertTrue(loaded.blocks("x.tracker.net"))
        assertFalse(loaded.blocks("example.com"))
        assertFalse(loaded.blocks("net"))
    }

    @Test fun emptyListRoundTrips() {
        val loaded = Blocklist.fromFile(write("empty.bin", emptyList()))
        assertNotNull(loaded)
        assertEquals(0, loaded!!.size)
        assertFalse(loaded.blocks("example.com"))
    }

    @Test fun eightyThousandEntriesLoadAndLookUp() {
        val domains = (0 until 80_000).map { "d$it.example.net" }
        val file = write("big.bin", domains)
        assertEquals(16L + 80_000L * 8, file.length())

        val start = System.nanoTime()
        val loaded = Blocklist.fromFile(file)!!
        val loadMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${loadMs}ms", loadMs < 1000)

        assertEquals(80_000, loaded.size)
        assertTrue(loaded.blocks("d0.example.net"))
        assertTrue(loaded.blocks("www.d79999.example.net"))
        assertFalse(loaded.blocks("d80000.example.net"))
        assertFalse(loaded.blocks("example.net"))
    }

    @Test fun countParameterWritesOnlyThePrefix() {
        val sorted = Blocklist.sortedHashes(listOf("a.com", "b.com", "c.com"))
        val file = File(tmp.root, "prefix.bin")
        Blocklist.writeFile(file, sorted, 2)
        assertEquals(2, Blocklist.fromFile(file)!!.size)
    }

    @Test fun rewritingReplacesTheFileAndLeavesNoTempFile() {
        val file = write("list.bin", listOf("old.example.com"))
        Blocklist.writeFile(file, Blocklist.sortedHashes(listOf("new.example.com")))
        val loaded = Blocklist.fromFile(file)!!
        assertTrue(loaded.blocks("new.example.com"))
        assertFalse(loaded.blocks("old.example.com"))
        assertFalse(File(tmp.root, "list.bin.tmp").exists())
    }

    @Test fun missingFileIsNull() {
        assertNull(Blocklist.fromFile(File(tmp.root, "nope.bin")))
    }

    @Test fun corruptFilesAreRejected() {
        val good = write("good.bin", listOf("a.example.com", "b.example.com"))
        val bytes = good.readBytes()

        fun variant(name: String, content: ByteArray): File =
            File(tmp.root, name).also { it.writeBytes(content) }

        assertNull("tiny", Blocklist.fromFile(variant("tiny.bin", ByteArray(5))))
        assertNull("truncated", Blocklist.fromFile(variant("cut.bin", bytes.copyOf(bytes.size - 3))))
        assertNull("extra bytes", Blocklist.fromFile(variant("extra.bin", bytes + ByteArray(8))))

        val badMagic = bytes.copyOf().also { it[0] = 0 }
        assertNull("magic", Blocklist.fromFile(variant("magic.bin", badMagic)))

        val badVersion = bytes.copyOf().also { it[7] = 9 }
        assertNull("version", Blocklist.fromFile(variant("version.bin", badVersion)))

        val badCount = bytes.copyOf().also { it[15] = 7 }
        assertNull("count", Blocklist.fromFile(variant("count.bin", badCount)))
    }

    @Test fun directoryIsNotAList() {
        assertNull(Blocklist.fromFile(tmp.newFolder("dir")))
        RandomAccessFile(File(tmp.root, "x"), "rw").use { it.setLength(0) }
        assertNull(Blocklist.fromFile(File(tmp.root, "x")))
    }
}
