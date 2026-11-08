package com.altf4.hush.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HashBufferTest {
    @Test fun sortsAndDeduplicates() {
        val buffer = HashBuffer(16)
        for (v in longArrayOf(5, -3, 5, 0, Long.MAX_VALUE, Long.MIN_VALUE, -3)) buffer.add(v)
        assertEquals(7, buffer.size) // size counts duplicates
        assertArrayEquals(
            longArrayOf(Long.MIN_VALUE, -3, 0, 5, Long.MAX_VALUE),
            buffer.sortedUnique(),
        )
    }

    @Test fun growsPastItsInitialCapacity() {
        val buffer = HashBuffer(16)
        for (i in 0 until 1000) buffer.add((999 - i).toLong())
        assertEquals(1000, buffer.size)
        val sorted = buffer.sortedUnique()
        assertEquals(1000, sorted.size)
        assertEquals(0L, sorted.first())
        assertEquals(999L, sorted.last())
    }

    @Test fun truncateDiscardsAHalfReadList() {
        val buffer = HashBuffer(16)
        for (v in longArrayOf(1, 2, 3, 40, 50)) buffer.add(v)
        buffer.truncate(3)
        assertEquals(3, buffer.size)
        assertArrayEquals(longArrayOf(1, 2, 3), buffer.sortedUnique())
        buffer.truncate(10) // cannot grow
        assertEquals(3, buffer.size)
    }

    @Test fun emptyBufferGivesEmptyArray() {
        assertEquals(0, HashBuffer().sortedUnique().size)
    }
}
