package de.kvxd.kmcprotocol

import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.SerializersModule
import java.nio.ByteBuffer

class MinecraftPacketEncoder : AbstractEncoder() {
    private val buffer = ByteBuffer.allocate(1024) // You might want to make this dynamic

    override val serializersModule: SerializersModule = SerializersModule {}

    fun writeBytes(bytes: ByteArray) {
        buffer.put(bytes)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        buffer.put(VarInt.encode(bytes.size))
        buffer.put(bytes)
    }

    fun writeShort(value: Short) {
        buffer.putShort(value)
    }

    fun getBytes(): ByteArray {
        buffer.flip()
        val result = ByteArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    override fun encodeString(value: String) {
        writeString(value)
    }

    override fun encodeShort(value: Short) {
        writeShort(value)
    }

    override fun encodeInt(value: Int) {
        writeBytes(VarInt.encode(value))
    }
}