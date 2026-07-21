package dev.vinkyv.tvcontrol.protocol.codec

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.min

class OPACK {
    fun pack(data: Any?): ByteArray {
        val objectList = mutableListOf<ByteArray>()
        return _pack(data, objectList)
    }

    private fun _pack(data: Any?, objectList: MutableList<ByteArray>): ByteArray {
        val packed: ByteArray = when (data) {
            null -> byteArrayOf(0x04)
            is Boolean -> byteArrayOf(if (data) 0x01 else 0x02)
            is UUID -> byteArrayOf(0x05) + data.toString().toByteArray()
            is Date -> throw NotImplementedError("Absolute time packing not implemented")
            is Int -> packInt(data)
            is Long -> packLong(data) // Treat Long as potential large int
            is Float -> byteArrayOf(0x35) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(data).array()
            is Double -> byteArrayOf(0x36) + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(data).array()
            is String -> packString(data)
            is ByteArray -> packData(data)
            is List<*> -> packList(data, objectList)
            is Map<*, *> -> packMap(data, objectList)
            else -> throw IllegalArgumentException("Unsupported type: ${data::class.java}")
        }

        // Reuse if already in object list (deduplication)
        val existingIndex = objectList.indexOfFirst { it.contentEquals(packed) }
        if (existingIndex != -1) {
            return packReference(existingIndex)
        }

        if (packed.size > 1) {
            objectList.add(packed)
        }
        return packed
    }

    private fun packInt(value: Int): ByteArray {
        return when {
            value in 0..0x27 -> byteArrayOf((value + 8).toByte())
            value in 0..0xFF -> byteArrayOf(0x30) + value.toByte()
            value in 0..0xFFFF -> byteArrayOf(0x31) + shortToLittleEndian(value.toShort())
            value in 0..0xFFFFFFFF -> byteArrayOf(0x32) + intToLittleEndian(value)
            else -> byteArrayOf(0x33) + longToLittleEndian(value.toLong())
        }
    }

    private fun packLong(value: Long): ByteArray {
        return when {
            value in 0..0x27 -> byteArrayOf((value + 8).toByte())
            value in 0..0xFF -> byteArrayOf(0x30) + value.toByte()
            value in 0..0xFFFF -> byteArrayOf(0x31) + shortToLittleEndian(value.toShort())
            value in 0..0xFFFFFFFF -> byteArrayOf(0x32) + intToLittleEndian(value.toInt())
            else -> byteArrayOf(0x33) + longToLittleEndian(value)
        }
    }

    private fun packString(str: String): ByteArray {
        val encoded = str.toByteArray(Charsets.UTF_8)
        val len = encoded.size
        return when {
            len <= 0x20 -> byteArrayOf((0x40 + len).toByte()) + encoded
            len <= 0xFF -> byteArrayOf(0x61) + len.toByte() + encoded
            len <= 0xFFFF -> byteArrayOf(0x62) + shortToLittleEndian(len.toShort()) + encoded
            len <= 0xFFFFFF -> byteArrayOf(0x63) + threeByteLength(len) + encoded
            len <= 0xFFFFFFFF -> byteArrayOf(0x64) + intToLittleEndian(len) + encoded
            else -> throw IllegalArgumentException("String too long")
        }
    }

    private fun packData(data: ByteArray): ByteArray {
        val len = data.size
        return when {
            len <= 0x20 -> byteArrayOf((0x70 + len).toByte()) + data
            len <= 0xFF -> byteArrayOf(0x91.toByte()) + len.toByte() + data
            len <= 0xFFFF -> byteArrayOf(0x92.toByte()) + shortToLittleEndian(len.toShort()) + data
            len <= 0xFFFFFFFF -> byteArrayOf(0x93.toByte()) + intToLittleEndian(len) + data
            else -> byteArrayOf(0x94.toByte()) + longToLittleEndian(len.toLong()) + data
        }
    }

    private fun packList(list: List<*>, objectList: MutableList<ByteArray>): ByteArray {
        val count = min(list.size, 0xF)
        val header = byteArrayOf((0xD0 + count).toByte())
        val content = list.flatMap { _pack(it, objectList).asList() }.toByteArray()
        return if (list.size >= 0xF) {
            header + content + byteArrayOf(0x03)
        } else {
            header + content
        }
    }

    private fun packMap(map: Map<*, *>, objectList: MutableList<ByteArray>): ByteArray {
        val count = min(map.size, 0xF)
        val header = byteArrayOf((0xE0 + count).toByte())
        val content = map.flatMap { (k, v) ->
            _pack(k, objectList).asList() + _pack(v, objectList).asList()
        }.toByteArray()
        return if (map.size >= 0xF) {
            header + content + byteArrayOf(0x03)
        } else {
            header + content
        }
    }

    private fun packReference(index: Int): ByteArray {
        return when {
            index < 0x21 -> byteArrayOf((0xA0 + index).toByte())
            index <= 0xFF -> byteArrayOf(0xC1.toByte()) + index.toByte()
            index <= 0xFFFF -> byteArrayOf(0xC2.toByte()) + shortToLittleEndian(index.toShort())
            index <= 0xFFFFFFFF -> byteArrayOf(0xC3.toByte()) + intToLittleEndian(index)
            else -> byteArrayOf(0xC4.toByte()) + longToLittleEndian(index.toLong())
        }
    }

    // Helper conversions
    private fun shortToLittleEndian(value: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()

    private fun intToLittleEndian(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun longToLittleEndian(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    private fun threeByteLength(len: Int): ByteArray {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(len.toByte())
        buf.put((len shr 8).toByte())
        buf.put((len shr 16).toByte())
        return buf.array()
    }

    /**
     * Unpacks raw OPACK data into Kotlin objects. Returns the unpacked value and any remaining bytes.
     */
    fun unpack(data: ByteArray): Pair<Any?, ByteArray> {
        val objectList = mutableListOf<Any?>()
        return _unpack(data, objectList)
    }

    private fun _unpack(data: ByteArray, objectList: MutableList<Any?>): Pair<Any?, ByteArray> {
        if (data.isEmpty()) throw IllegalArgumentException("Empty data")

        val type = data[0].toInt() and 0xFF
        var value: Any? = null
        var remaining = data.copyOfRange(1, data.size)
        var addToObjectList = true

        when {
            type == 0x01 -> {
                value = true
                addToObjectList = false
            }
            type == 0x02 -> {
                value = false
                addToObjectList = false
            }
            type == 0x04 -> {
                value = null
                addToObjectList = false
            }
            type == 0x05 -> {
                if (remaining.size < 16) throw IllegalArgumentException("Invalid UUID")
                value = UUID.nameUUIDFromBytes(remaining.copyOfRange(0, 16))
                remaining = remaining.copyOfRange(16, remaining.size)
            }
            type == 0x06 -> {
                // Dummy: parse as long
                if (remaining.size < 8) throw IllegalArgumentException("Invalid time")
                value = ByteBuffer.wrap(remaining.copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).long
                remaining = remaining.copyOfRange(8, remaining.size)
            }
            type in 0x08..0x2F -> {
                value = type - 8
                addToObjectList = false
            }
            type == 0x35 -> {
                if (remaining.size < 4) throw IllegalArgumentException("Invalid float")
                value = ByteBuffer.wrap(remaining.copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).float
                remaining = remaining.copyOfRange(4, remaining.size)
            }
            type == 0x36 -> {
                if (remaining.size < 8) throw IllegalArgumentException("Invalid double")
                value = ByteBuffer.wrap(remaining.copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).double
                remaining = remaining.copyOfRange(8, remaining.size)
            }
            (type and 0xF0) == 0x30 -> {
                val noofBytes = 1 shl (type and 0x0F)
                if (remaining.size < noofBytes) throw IllegalArgumentException("Invalid int")
                value = when (noofBytes) {
                    1 -> remaining[0].toInt() and 0xFF
                    2 -> ByteBuffer.wrap(remaining.copyOfRange(0, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    4 -> ByteBuffer.wrap(remaining.copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).int
                    8 -> ByteBuffer.wrap(remaining.copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).long.toInt() // truncate if needed
                    else -> throw IllegalArgumentException("Unsupported int size")
                }
                remaining = remaining.copyOfRange(noofBytes, remaining.size)
            }
            type in 0x40..0x60 -> {
                val length = type - 0x40
                if (remaining.size < length) throw IllegalArgumentException("Invalid string")
                value = remaining.copyOfRange(0, length).toString(Charsets.UTF_8)
                remaining = remaining.copyOfRange(length, remaining.size)
            }
            type in 0x61..0x64 -> {
                val noofBytes = type and 0x0F
                if (remaining.size < noofBytes) throw IllegalArgumentException("Invalid string length")
                val length = when (noofBytes) {
                    1 -> remaining[0].toInt() and 0xFF
                    2 -> ByteBuffer.wrap(remaining.copyOfRange(0, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    3 -> {
                        val b1 = remaining[0].toInt() and 0xFF
                        val b2 = remaining[1].toInt() and 0xFF
                        val b3 = remaining[2].toInt() and 0xFF
                        (b3 shl 16) or (b2 shl 8) or b1
                    }
                    4 -> ByteBuffer.wrap(remaining.copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).int
                    else -> throw IllegalArgumentException("Invalid string length prefix")
                }
                val start = noofBytes
                if (remaining.size < start + length) throw IllegalArgumentException("Invalid string data")
                value = remaining.copyOfRange(start, start + length).toString(Charsets.UTF_8)
                remaining = remaining.copyOfRange(start + length, remaining.size)
            }
            type in 0x70..0x90 -> {
                val length = type - 0x70
                if (remaining.size < length) throw IllegalArgumentException("Invalid data")
                value = remaining.copyOfRange(0, length)
                remaining = remaining.copyOfRange(length, remaining.size)
            }
            type in 0x91..0x94 -> {
                val noofBytes = 1 shl ((type and 0x0F) - 1)
                if (remaining.size < noofBytes) throw IllegalArgumentException("Invalid data length")
                val length = when (noofBytes) {
                    1 -> remaining[0].toInt() and 0xFF
                    2 -> ByteBuffer.wrap(remaining.copyOfRange(0, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    4 -> ByteBuffer.wrap(remaining.copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).int
                    8 -> ByteBuffer.wrap(remaining.copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).long.toInt() // truncate
                    else -> throw IllegalArgumentException("Unsupported data size")
                }
                val start = noofBytes
                if (remaining.size < start + length) throw IllegalArgumentException("Invalid data")
                value = remaining.copyOfRange(start, start + length)
                remaining = remaining.copyOfRange(start + length, remaining.size)
            }
            (type and 0xF0) == 0xD0 -> {
                val count = type and 0x0F
                val result = mutableListOf<Any?>()
                var ptr = remaining
                if (count == 0xF) {
                    while (ptr.isNotEmpty() && ptr[0].toInt() != 0x03) {
                        val (v, r) = _unpack(ptr, objectList)
                        result.add(v)
                        ptr = r
                    }
                    if (ptr.isNotEmpty()) ptr = ptr.copyOfRange(1, ptr.size)
                } else {
                    for (i in 0 until count) {
                        val (v, r) = _unpack(ptr, objectList)
                        result.add(v)
                        ptr = r
                    }
                }
                value = result
                remaining = ptr
                addToObjectList = false
            }
            (type and 0xE0) == 0xE0 -> {
                val count = type and 0x0F
                val result = mutableMapOf<Any?, Any?>()
                var ptr = remaining
                if (count == 0xF) {
                    while (ptr.isNotEmpty() && ptr[0].toInt() != 0x03) {
                        val (k, r1) = _unpack(ptr, objectList)
                        val (v, r2) = _unpack(r1, objectList)
                        result[k] = v
                        ptr = r2
                    }
                    if (ptr.isNotEmpty()) ptr = ptr.copyOfRange(1, ptr.size)
                } else {
                    for (i in 0 until count) {
                        val (k, r1) = _unpack(ptr, objectList)
                        val (v, r2) = _unpack(r1, objectList)
                        result[k] = v
                        ptr = r2
                    }
                }
                value = result
                remaining = ptr
                addToObjectList = false
            }
            type in 0xA0..0xC0 -> {
                val idx = type - 0xA0
                if (idx >= objectList.size) throw IndexOutOfBoundsException("Invalid object reference")
                value = objectList[idx]
            }
            type in 0xC1..0xC4 -> {
                val length = type - 0xC0
                if (remaining.size < length) throw IllegalArgumentException("Invalid reference")
                val uid = when (length) {
                    1 -> remaining[0].toInt() and 0xFF
                    2 -> ByteBuffer.wrap(remaining.copyOfRange(0, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    4 -> ByteBuffer.wrap(remaining.copyOfRange(0, 4)).order(ByteOrder.LITTLE_ENDIAN).int
                    else -> throw IllegalArgumentException("Invalid reference size")
                }
                if (uid >= objectList.size) throw IndexOutOfBoundsException("Invalid object reference")
                value = objectList[uid]
                remaining = remaining.copyOfRange(length, remaining.size)
            }
            else -> throw IllegalArgumentException("Unknown OPACK type: 0x${type.toString(16)}")
        }

        if (addToObjectList && value !in objectList) {
            objectList.add(value)
        }

        return value to remaining
    }
}