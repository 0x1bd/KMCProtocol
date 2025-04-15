package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.format.UseJson
import de.kvxd.kmcprotocol.core.format.number.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.text.toByteArray

class MinecraftBytePacketEncoder(private val scope: EncodingScope) : Encoder, CompositeEncoder {

    override val serializersModule: SerializersModule = scope.protocolData.serializersModule

    val builder = BytePacketBuilder()

    inline fun <reified T : MinecraftPacket> encodePacket(packet: T): ByteArray {
        val serializer = serializer<T>()

        encodeSerializableValue(serializer, packet)

        return builder.build().readByteArray()
    }

    override fun encodeByte(value: Byte) = runBlocking {
        builder.writeByte(value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) = runBlocking {
        encodeByte(value)
    }

    override fun encodeBoolean(value: Boolean) = runBlocking {
        builder.writeByte(if (value) 0x01 else 0x00)
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        encodeBoolean(value)
    }

    override fun encodeChar(value: Char) = runBlocking {
        builder.writeInt(value.code)
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        encodeChar(value)
    }

    override fun encodeDouble(value: Double) = runBlocking {
        builder.writeDouble(value)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        encodeDouble(value)
    }

    override fun encodeFloat(value: Float) = runBlocking {
        builder.writeFloat(value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        encodeFloat(value)
    }

    override fun encodeInt(value: Int) = runBlocking {
        builder.writeInt(value)
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) = runBlocking {
        val format = descriptor.getElementAnnotationFromIndex<IntFormat>(index)?.format ?: IntFormatType.FIXED

        when (format) {
            IntFormatType.FIXED -> encodeInt(value)
            IntFormatType.VARIABLE -> builder.writeVarInt(value)
        }
    }

    override fun encodeLong(value: Long) = runBlocking {
        builder.writeLong(value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) = runBlocking {
        val format = descriptor.getElementAnnotationFromIndex<LongFormat>(index)?.format ?: LongFormatType.FIXED

        when (format) {
            LongFormatType.FIXED -> encodeLong(value)
            LongFormatType.VARIABLE -> builder.writeVarLong(value)
        }
    }

    override fun encodeShort(value: Short) = runBlocking {
        builder.writeShort(value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        encodeShort(value)
    }

    override fun encodeString(value: String) = runBlocking {
        println("Encoding string: $value")
        val bytes = value.toByteArray()

        builder.writeVarInt(bytes.size)
        builder.writeFully(bytes)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        encodeString(value)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = runBlocking {
        val format =
            enumDescriptor.getAnnotation<IntFormat>()?.format ?: enumDescriptor.getAnnotation<LongFormat>()?.format
            ?: IntFormatType.VARIABLE

        when (format) {
            IntFormatType.FIXED -> encodeInt(index)
            IntFormatType.VARIABLE -> builder.writeVarInt(index)

            LongFormatType.FIXED -> encodeLong(index.toLong())
            LongFormatType.VARIABLE -> builder.writeVarLong(index.toLong())

            else -> error("Invalid format")
        }
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder = this

    @ExperimentalSerializationApi
    override fun encodeNull() {
        error("Cannot encode null")
    }

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() {
        error("Cannot encode null")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableValue(serializer: SerializationStrategy<T>, value: T?) {
        error("Cannot encode nullable")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        error("Cannot encode nullable")
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        when {
            descriptor.getElementAnnotationFromIndex<UseJson>(index) != null ->
                encodeString(scope.protocolData.json.encodeToString(serializer, value))

            else -> encodeSerializableValue(serializer, value)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {

    }

}