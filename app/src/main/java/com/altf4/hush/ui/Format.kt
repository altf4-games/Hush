package com.altf4.hush.ui

import android.icu.text.CompactDecimalFormat
import java.util.Locale

/** Groups a number with thousands separators, e.g. 81,234. */
fun formatCount(n: Long): String = "%,d".format(n)

fun formatCount(n: Int): String = formatCount(n.toLong())

/** Short form for tiles: exact below 10,000, then 12.4K, 1.2M. */
fun compactCount(n: Long): String =
    if (n < 10_000) formatCount(n)
    else CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(n)

fun compactCount(n: Int): String = compactCount(n.toLong())
