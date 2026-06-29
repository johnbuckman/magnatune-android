package com.magnatune.player.peer

import java.io.ByteArrayOutputStream

/**
 * Minimal Apple binary-plist (bplist00) codec — enough for the AirPlay-2 SETUP request/response
 * plists. Supports dict (Map), array (List), String (ASCII), Int/Long, Boolean, and ByteArray.
 */
object BPlist {

    // ---- encode ----
    fun encode(root: Any?): ByteArray {
        val flat = ArrayList<Any?>()
        val dictRefs = HashMap<Int, Pair<IntArray, IntArray>>()
        val arrRefs = HashMap<Int, IntArray>()

        fun collect(o: Any?): Int {
            val idx = flat.size
            flat.add(o)
            when (o) {
                is Map<*, *> -> {
                    val ks = IntArray(o.size); val vs = IntArray(o.size); var i = 0
                    for ((k, v) in o) { ks[i] = collect(k.toString()); vs[i] = collect(v); i++ }
                    dictRefs[idx] = ks to vs
                }
                is List<*> -> {
                    val es = IntArray(o.size); var i = 0
                    for (e in o) es[i++] = collect(e)
                    arrRefs[idx] = es
                }
            }
            return idx
        }
        collect(root)

        val n = flat.size
        val refSize = sizeFor(n - 1)
        val objs = ArrayList<ByteArray>(n)
        for (i in 0 until n) objs.add(encodeObj(flat[i], dictRefs[i], arrRefs[i], refSize))

        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray())
        val offsets = IntArray(n)
        for (i in 0 until n) { offsets[i] = out.size(); out.write(objs[i]) }
        val tableOffset = out.size()
        val offSize = sizeFor(tableOffset)
        for (off in offsets) writeN(out, off.toLong(), offSize)
        // trailer: 6 unused/sortVersion, offsetSize, refSize, numObjects(8), topObject(8), tableOffset(8)
        out.write(ByteArray(6)); out.write(offSize); out.write(refSize)
        writeN(out, n.toLong(), 8); writeN(out, 0L, 8); writeN(out, tableOffset.toLong(), 8)
        return out.toByteArray()
    }

    private fun sizeFor(maxVal: Int): Int =
        if (maxVal <= 0xFF) 1 else if (maxVal <= 0xFFFF) 2 else 4

    private fun writeN(out: ByteArrayOutputStream, v: Long, size: Int) {
        for (i in size - 1 downTo 0) out.write(((v shr (8 * i)) and 0xFF).toInt())
    }

    private fun encodeObj(o: Any?, dref: Pair<IntArray, IntArray>?, aref: IntArray?, refSize: Int): ByteArray {
        val b = ByteArrayOutputStream()
        when (o) {
            null -> b.write(0x00)
            is Boolean -> b.write(if (o) 0x09 else 0x08)
            is Int -> writeInt(b, o.toLong())
            is Long -> writeInt(b, o)
            is String -> { writeMarker(b, 0x50, o.length); b.write(o.toByteArray(Charsets.US_ASCII)) }
            is ByteArray -> { writeMarker(b, 0x40, o.size); b.write(o) }
            is Map<*, *> -> {
                writeMarker(b, 0xD0, o.size)
                val (ks, vs) = dref!!
                for (k in ks) writeN(b, k.toLong(), refSize)
                for (v in vs) writeN(b, v.toLong(), refSize)
            }
            is List<*> -> {
                writeMarker(b, 0xA0, o.size)
                for (e in aref!!) writeN(b, e.toLong(), refSize)
            }
            else -> throw IllegalArgumentException("bplist: unsupported ${o::class}")
        }
        return b.toByteArray()
    }

    private fun writeInt(b: ByteArrayOutputStream, v: Long) {
        when {
            v in 0..0xFF -> { b.write(0x10); b.write(v.toInt()) }
            v in 0..0xFFFF -> { b.write(0x11); writeN(b, v, 2) }
            v in 0..0xFFFFFFFFL -> { b.write(0x12); writeN(b, v, 4) }
            else -> { b.write(0x13); writeN(b, v, 8) }
        }
    }

    private fun writeMarker(b: ByteArrayOutputStream, base: Int, count: Int) {
        if (count < 15) b.write(base or count)
        else { b.write(base or 0x0F); writeInt(b, count.toLong()) }
    }

    // ---- decode ----
    fun decode(data: ByteArray): Any? {
        val n = readBE(data, data.size - 24, 8).toInt()
        val offSize = data[data.size - 26].toInt() and 0xFF
        val refSize = data[data.size - 25].toInt() and 0xFF
        val top = readBE(data, data.size - 16, 8).toInt()
        val tableOffset = readBE(data, data.size - 8, 8).toInt()
        val offsets = IntArray(n) { readBE(data, tableOffset + it * offSize, offSize).toInt() }
        fun parse(idx: Int): Any? {
            var p = offsets[idx]
            val marker = data[p].toInt() and 0xFF
            val type = marker and 0xF0
            val nib = marker and 0x0F
            p++
            fun count(): Int {
                if (nib != 0x0F) return nib
                val m2 = data[p].toInt() and 0xFF; p++
                val cnt = readBE(data, p, 1 shl (m2 and 0x0F)).toInt(); p += 1 shl (m2 and 0x0F)
                return cnt
            }
            return when (type) {
                0x00 -> when (marker) { 0x08 -> false; 0x09 -> true; else -> null }
                0x10 -> readBE(data, p, 1 shl nib)
                0x40 -> { val c = count(); data.copyOfRange(p, p + c) }
                0x50 -> { val c = count(); String(data, p, c, Charsets.US_ASCII) }
                0x60 -> { val c = count(); String(data, p, c * 2, Charsets.UTF_16BE) }
                0xA0 -> { val c = count(); (0 until c).map { parse(readBE(data, p + it * refSize, refSize).toInt()) } }
                0xD0 -> {
                    val c = count()
                    val keys = IntArray(c) { readBE(data, p + it * refSize, refSize).toInt() }
                    val vals = IntArray(c) { readBE(data, p + (c + it) * refSize, refSize).toInt() }
                    val m = LinkedHashMap<String, Any?>()
                    for (i in 0 until c) m[parse(keys[i]).toString()] = parse(vals[i])
                    m
                }
                else -> null
            }
        }
        return parse(top)
    }

    private fun readBE(d: ByteArray, off: Int, size: Int): Long {
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or (d[off + i].toLong() and 0xFF)
        return v
    }
}
