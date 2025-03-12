package de.kvxd.kmcprotocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import java.nio.ByteBuffer

class MinecraftPacketDecoder(private val buffer: ByteBuffer) : AbstractDecoder() {
    override val serializersModule: SerializersModule = SerializersModule {}

    private var elementIndex = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

    override fun decodeString(): String {
        return ByteBufferUtils.readString(buffer)
    }

    override fun decodeShort(): Short {
        return buffer.short
    }

    override fun decodeInt(): Int {
        return ByteBufferUtils.readVarInt(buffer)
    }

    override fun decodeByte(): Byte = buffer.get()

    override fun decodeBoolean(): Boolean = buffer.get() != 0.toByte()

    override fun decodeLong(): Long = buffer.long

    override fun decodeFloat(): Float = buffer.float

    override fun decodeDouble(): Double = buffer.double

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        return ByteBufferUtils.readVarInt(buffer)
    }
}