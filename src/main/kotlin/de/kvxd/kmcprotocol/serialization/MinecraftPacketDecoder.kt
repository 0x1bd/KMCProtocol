package de.kvxd.kmcprotocol.serialization

import de.kvxd.kmcprotocol.MinecraftTypes
import de.kvxd.kmcprotocol.datatypes.VarInt
import kotlinx.io.Source
import kotlinx.io.readDouble
import kotlinx.io.readFloat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)

class MinecraftPacketDecoder(private val packet: Source) : AbstractDecoder() {
    override val serializersModule: SerializersModule = SerializersModule {}

    private var elementIndex = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

    override fun decodeString(): String {
        return MinecraftTypes.readString(packet)
    }

    override fun decodeShort(): Short {
        return packet.readShort()
    }

    override fun decodeInt(): Int {
        return packet.readInt()
    }

    fun decodeVarInt(): Int {
        return VarInt.decode(packet)
    }

    override fun decodeByte(): Byte = packet.readByte()

    override fun decodeBoolean(): Boolean = packet.readByte() != 0.toByte()

    override fun decodeLong(): Long = packet.readLong()

    override fun decodeFloat(): Float = packet.readFloat()

    override fun decodeDouble(): Double = packet.readDouble()

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        return VarInt.decode(packet)
    }
}