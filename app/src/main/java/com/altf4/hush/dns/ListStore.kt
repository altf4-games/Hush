package com.altf4.hush.dns

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/** Where the merged, downloaded list lives, and the starter list used before the first update. */
object ListStore {
    private const val FILE_NAME = "blocklist.bin"
    private const val STARTER_ASSET = "default_blocklist.txt"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** The downloaded list if present and valid, otherwise the small bundled starter list. */
    fun load(context: Context): Blocklist =
        Blocklist.fromFile(file(context)) ?: starter(context)

    private fun starter(context: Context): Blocklist {
        val buffer = HashBuffer(256)
        try {
            context.assets.open(STARTER_ASSET).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    ListParser.collect(reader, ListFormat.Hosts, buffer)
                }
            }
        } catch (e: IOException) {
            // No starter list: run without one.
        }
        return Blocklist.fromSorted(buffer.sortedUnique())
    }
}
