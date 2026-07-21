package dev.vinkyv.tvcontrol.protocol.codec

enum class TlvValue(val hex: Int) {
    Method(0x00),
    Identifier(0x01),
    Salt(0x02),
    PublicKey(0x03),
    Proof(0x04),
    EncryptedData(0x05),
    SeqNo(0x06),
    Error(0x07),
    BackOff(0x08),
    Certificate(0x09),
    Signature(0x0A),
    Permissions(0x0B),
    FragmentData(0x0C),
    FragmentLast(0x0D),
    Name(0x11),
    Flags(0x13)
}

fun readTLV(data: ByteArray) : Map<Int, ByteArray> {
    val result = mutableMapOf<Int, ByteArray>()
    var pos = 0

    while (pos < data.size) {
        // Convert signed byte to unsigned integer
        val tag = data[pos].toInt() and 0xFF
        val length = data[pos + 1].toInt() and 0xFF

        val value = data.copyOfRange(pos + 2, pos + 2 + length)

        if (result.containsKey(tag)) {
            result[tag] = result[tag]!! + value
        } else {
            result[tag] = value
        }

        pos += 2 + length
    }
    return result
}

fun writeTLV(data: Map<Int, ByteArray>): ByteArray {
    var tlv = byteArrayOf()

    for ((key, value) in data) {
        val tag = key.toByte()
        var length = value.size
        var pos = 0

        while (pos < value.size) {
            val size = minOf(length, 255)

            tlv += tag
            tlv += size.toByte()
            tlv += value.copyOfRange(pos, pos + size)

            pos += size
            length -= size
        }
    }
    return tlv
}
