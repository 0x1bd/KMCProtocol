package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.format.UseJson
import de.kvxd.kmcprotocol.core.format.number.*
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class MinecraftBytePacketDecoder(private val scope: EncodingScope, private val channel: ByteReadChannel) : Decoder,
    CompositeDecoder {

    override val serializersModule: SerializersModule = scope.protocolData.serializersModule

    private var currentIndex = 0

    @OptIn(InternalSerializationApi::class)
    fun decodePacket(id: Int, direction: Direction): MinecraftPacket {
        println("decoding: $id $direction")

        val packetClass = scope.protocolData.registry.getPacketClass(id, direction)

        //val serializer = scope.protocolData.registry.getMetadata(packetClass).serializer

        return packetClass.serializer().deserialize(this)
    }

    override fun decodeByte(): Byte = runBlocking { channel.readByte() }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        return decodeByte()
    }

    override fun decodeBoolean(): Boolean = runBlocking { channel.readByte() == 0x01.toByte() }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        return decodeBoolean()
    }

    override fun decodeChar(): Char = runBlocking { channel.readInt().toChar() }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
        return decodeChar()
    }

    override fun decodeDouble(): Double = runBlocking { channel.readDouble() }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
        return decodeDouble()
    }

    override fun decodeFloat(): Float = runBlocking { channel.readFloat() }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
        return decodeFloat()
    }

    override fun decodeInt(): Int = runBlocking { channel.readInt() }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = runBlocking {
        val format = descriptor.getElementAnnotationFromIndex<IntFormat>(index)?.format ?: IntFormatType.FIXED

        return@runBlocking when (format) {
            IntFormatType.FIXED -> decodeInt()
            IntFormatType.VARIABLE -> channel.readVarInt()
        }
    }

    override fun decodeLong(): Long = runBlocking { channel.readLong() }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = runBlocking {
        val format = descriptor.getElementAnnotationFromIndex<LongFormat>(index)?.format ?: LongFormatType.FIXED

        return@runBlocking when (format) {
            LongFormatType.FIXED -> decodeLong()
            LongFormatType.VARIABLE -> channel.readVarLong()
        }
    }

    override fun decodeShort(): Short = runBlocking { channel.readShort() }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        return decodeShort()
    }

    override fun decodeString(): String = runBlocking {
        val length = channel.readVarInt()

        val bytes = channel.readByteArray(length)
        return@runBlocking String(bytes)
    }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        return decodeString()
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = runBlocking {
        val format =
            enumDescriptor.getAnnotation<IntFormat>()?.format ?: enumDescriptor.getAnnotation<LongFormat>()?.format
            ?: IntFormatType.VARIABLE

        return@runBlocking when (format) {
            IntFormatType.FIXED -> decodeInt()
            IntFormatType.VARIABLE -> channel.readVarInt()

            LongFormatType.FIXED -> decodeLong().toInt()
            LongFormatType.VARIABLE -> channel.readVarLong().toInt()

            else -> error("Invalid format")
        }
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = decodeInline(descriptor)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val index = currentIndex

        currentIndex++

        if (channel.availableForRead == 0) {
            currentIndex = 0
            return CompositeDecoder.DECODE_DONE
        }

        if (index >= descriptor.elementsCount) {
            currentIndex = 0
            return CompositeDecoder.DECODE_DONE
        }

        return index
    }

    override fun decodeNull(): Nothing? {
        error("Cannot decode null")
    }

    override fun decodeNotNullMark(): Boolean {
        error("Cannot decode null")
    }

    override fun <T : Any> decodeNullableSerializableValue(deserializer: DeserializationStrategy<T?>): T? {
        error("Cannot decode nullable")
    }

    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        error("Cannot decode nullable")
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        return when {
            descriptor.getElementAnnotationFromIndex<UseJson>(index) != null ->
                scope.protocolData.json.decodeFromString(deserializer, decodeString())

            else -> deserializer.deserialize(MinecraftBytePacketDecoder(scope, channel))
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

    override fun endStructure(descriptor: SerialDescriptor) {
        currentIndex = 0
    }


}