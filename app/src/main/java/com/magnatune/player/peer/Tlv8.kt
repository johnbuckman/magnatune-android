package com.magnatune.player.peer

import java.io.ByteArrayOutputStream

/** HAP/AirPlay-2 TLV8 codec (type:1, length:1, value; values >255 split into same-type fragments). */
object Tlv8 {
    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val STATE = 0x06
    const val ERROR = 0x07
    const val SIGNATURE = 0x0A
    const val FLAGS = 0x13

    fun encode(items: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((type, value) in items) {
            if (value.isEmpty()) { out.write(type); out.write(0); continue }
            var off = 0
            while (off < value.size) {
                val n = minOf(255, value.size - off)
                out.write(type); out.write(n); out.write(value, off, n); off += n
            }
        }
        return out.toByteArray()
    }

    /** Decode, concatenating consecutive fragments of the same type. */
    fun decode(data: ByteArray): Map<Int, ByteArray> {
        val acc = LinkedHashMap<Int, ByteArrayOutputStream>()
        var i = 0
        while (i + 1 < data.size) {
            val type = data[i].toInt() and 0xff
            val len = data[i + 1].toInt() and 0xff
            i += 2
            if (i + len > data.size) break
            acc.getOrPut(type) { ByteArrayOutputStream() }.write(data, i, len)
            i += len
        }
        return acc.mapValues { it.value.toByteArray() }
    }
}
