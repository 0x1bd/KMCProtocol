package de.kvxd.kmcprotocol

object VarInt {

    fun encode(value: Int): ByteArray {
        val bytes = mutableListOf<Byte>()
        var temp = value
        do {
            var byte = (temp and 127).toByte()
            temp = temp ushr 7
            if (temp != 0) {
                byte = (byte.toInt() or 128).toByte()
            }
            bytes.add(byte)
        } while (temp != 0)
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray, offset: Int = 0): Pair<Int, Int> {
        var value = 0
        var position = offset
        var currentByte: Byte
        var bytesRead = 0

        do {
            currentByte = bytes[position]
            value = value or ((currentByte.toInt() and 127) shl (7 * bytesRead))
            position++
            bytesRead++
            if (bytesRead > 5) {
                throw RuntimeException("VarInt is too big")
            }
        } while (currentByte.toInt() and 128 != 0)

        return Pair(value, bytesRead)
    }
}