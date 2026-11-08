package com.altf4.hush.data

/**
 * Counts of DNS lookups answered and blocked, kept per day for the last [KEEP_DAYS] days plus a
 * running lifetime total. Only numbers are stored, never names. Pure: callers pass the day in
 * (days since the epoch in local time), so nothing here reads the clock.
 */
class StatsLog {
    class Day(val day: Int, var total: Int = 0, var blocked: Int = 0)

    private val days = ArrayList<Day>() // ascending by day
    var lifetimeTotal = 0L
        private set
    var lifetimeBlocked = 0L
        private set

    fun record(day: Int, blocked: Boolean) {
        val d = dayEntry(day)
        d.total++
        lifetimeTotal++
        if (blocked) {
            d.blocked++
            lifetimeBlocked++
        }
    }

    /** Totals for [day], zero if nothing was recorded. */
    fun on(day: Int): Day = days.firstOrNull { it.day == day } ?: Day(day)

    /** The last [count] days ending at [today], oldest first; days without data are zero. */
    fun last(today: Int, count: Int = KEEP_DAYS): List<Day> =
        (today - count + 1..today).map { on(it) }

    fun clear() {
        days.clear()
        lifetimeTotal = 0
        lifetimeBlocked = 0
    }

    private fun dayEntry(day: Int): Day {
        val last = days.lastOrNull()
        if (last != null && last.day == day) return last
        // Out of order (the clock moved back) or a new day.
        days.firstOrNull { it.day == day }?.let { return it }
        val entry = Day(day)
        days.add(entry)
        days.sortBy { it.day }
        while (days.size > KEEP_DAYS) days.removeAt(0)
        return entry
    }

    /** `lifetimeTotal|lifetimeBlocked|day:total:blocked,day:total:blocked,...` */
    fun encode(): String =
        "$lifetimeTotal|$lifetimeBlocked|" + days.joinToString(",") { "${it.day}:${it.total}:${it.blocked}" }

    companion object {
        const val KEEP_DAYS = 7
        private const val MILLIS_PER_DAY = 86_400_000L

        /** Days since the epoch for [millis] in a zone [offsetMillis] ahead of UTC. */
        fun dayOf(millis: Long, offsetMillis: Int): Int =
            Math.floorDiv(millis + offsetMillis, MILLIS_PER_DAY).toInt()

        /** Reads [raw]; anything malformed gives an empty log rather than an error. */
        fun decode(raw: String?): StatsLog {
            val log = StatsLog()
            if (raw.isNullOrEmpty()) return log
            val parts = raw.split("|")
            if (parts.size != 3) return log
            val total = parts[0].toLongOrNull() ?: return log
            val blocked = parts[1].toLongOrNull() ?: return log
            if (total < 0 || blocked < 0 || blocked > total) return log
            val entries = ArrayList<Day>()
            if (parts[2].isNotEmpty()) {
                for (piece in parts[2].split(",")) {
                    val f = piece.split(":")
                    if (f.size != 3) return StatsLog()
                    val day = f[0].toIntOrNull() ?: return StatsLog()
                    val t = f[1].toIntOrNull() ?: return StatsLog()
                    val b = f[2].toIntOrNull() ?: return StatsLog()
                    if (t < 0 || b < 0 || b > t) return StatsLog()
                    entries.add(Day(day, t, b))
                }
            }
            entries.sortBy { it.day }
            log.days.addAll(entries.distinctBy { it.day }.takeLast(KEEP_DAYS))
            log.lifetimeTotal = total
            log.lifetimeBlocked = blocked
            return log
        }
    }
}
