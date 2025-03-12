package de.kvxd.kmcprotocol

import io.ktor.utils.io.core.*
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlin.text.toByteArray


class MinecraftPacketEncoder : AbstractEncoder() {

    private val builder = BytePacketBuilder()

    override val serializersModule: SerializersModule = SerializersModule {}

    fun writeBytes(bytes: ByteArray) {
        builder.writeFully(bytes)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        builder.writeFully(VarInt.encode(bytes.size))
        builder.writeFully(bytes)
    }

    fun writeShort(value: Short) {
        builder.writeShort(value)
    }

    fun getBytes(): ByteArray {
        return builder.build().readBytes()
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