package com.altf4.hush.dns

import java.util.Arrays

/** Growable collection of 64-bit hashes used while merging downloaded lists. */
class HashBuffer(initialCapacity: Int = 1 shl 16) {
    private var data = LongArray(maxOf(initialCapacity, 16))

    /** Number of hashes added so far, duplicates included. */
    var size = 0
        private set

    fun add(hash: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = hash
    }

    /** Sorted ascending with duplicates removed. */
    fun sortedUnique(): LongArray {
        Arrays.sort(data, 0, size)
        var m = 0
        for (i in 0 until size) {
            if (m == 0 || data[i] != data[m - 1]) data[m++] = data[i]
        }
        return data.copyOf(m)
    }
}
