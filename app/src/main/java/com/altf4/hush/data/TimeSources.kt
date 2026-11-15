package com.altf4.hush.data

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object Sntp {
    const val PORT = 123
    const val SIZE = 48
    private const val NTP_TO_UNIX_SECONDS = 2_208_988_800L
    private const val ERA_ONE_TO_UNIX_SECONDS = 2_085_978_496L

    fun request(nonce: ByteArray): ByteArray {
        require(nonce.size == 8)
        val packet = ByteArray(SIZE)
        packet[0] = 0x1B
        System.arraycopy(nonce, 0, packet, 40, 8)
        return packet
    }

    fun parse(reply: ByteArray, length: Int, nonce: ByteArray, roundTripMs: Long): Long? {
        if (length < SIZE || reply.size < SIZE) return null
        val first = reply[0].toInt() and 0xFF
        if ((first shr 6) == 3) return null
        if ((first and 7) != 4) return null
        val stratum = reply[1].toInt() and 0xFF
        if (stratum !in 1..15) return null
        for (i in 0 until 8) if (reply[24 + i] != nonce[i]) return null
        val seconds = u32(reply, 40)
        if (seconds == 0L) return null
        val fraction = u32(reply, 44)
        val unixSeconds = if ((seconds and 0x80000000L) != 0L) seconds - NTP_TO_UNIX_SECONDS else seconds + ERA_ONE_TO_UNIX_SECONDS
        val millis = unixSeconds * 1000 + (fraction * 1000 shr 32)
        return millis + roundTripMs / 2
    }

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    fun query(host: String, timeoutMs: Int, elapsed: () -> Long): Long? {
        val nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val request = request(nonce)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(host)
            val started = elapsed()
            socket.send(DatagramPacket(request, request.size, address, PORT))
            val buffer = ByteArray(128)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            if (reply.address != address) return null
            return parse(buffer, reply.length, nonce, elapsed() - started)
        }
    }
}

object HttpsDate {
    fun parse(header: String?, roundTripMs: Long): Long? {
        if (header == null) return null
        return try {
            ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() +
                500 + roundTripMs / 2
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun query(url: String, timeoutMs: Int, elapsed: () -> Long): Long? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            val started = elapsed()
            connection.responseCode
            return parse(connection.getHeaderField("Date"), elapsed() - started)
        } finally {
            connection.disconnect()
        }
    }
}
