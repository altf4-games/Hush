package com.altf4.hush.data

data class TimeAnchor(val boot: Int, val serverMs: Long, val elapsedMs: Long, val skewMs: Long)

interface TimeStore {
    fun load(): TimeAnchor?
    fun save(anchor: TimeAnchor)
}

class TrustedClock(
    private val wall: () -> Long,
    private val elapsed: () -> Long,
    private val boot: () -> Int,
    private val store: TimeStore,
) {
    @Volatile private var anchor: TimeAnchor? = store.load()

    private fun sameBoot(a: TimeAnchor): Boolean = a.boot >= 0 && a.boot == boot()

    fun now(): Long {
        val a = anchor ?: return wall()
        return if (sameBoot(a)) a.serverMs + (elapsed() - a.elapsedMs) else wall() + a.skewMs
    }

    fun record(serverMs: Long) {
        val a = TimeAnchor(boot(), serverMs, elapsed(), serverMs - wall())
        anchor = a
        store.save(a)
    }

    val synced: Boolean get() = anchor?.let { sameBoot(it) } == true

    fun ageMs(): Long {
        val a = anchor ?: return Long.MAX_VALUE
        return if (sameBoot(a)) elapsed() - a.elapsedMs else Long.MAX_VALUE
    }
}
