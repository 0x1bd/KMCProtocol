package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.variant.*
import kotlinx.io.Sink
import kotlinx.io.writeDouble
import kotlinx.io.writeFloat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

class MinecraftEncoder(data: ProtocolData, private val sink: Sink) : Encoder, CompositeEncoder {

    override val serializersModule: SerializersModule = data.serializersModule

    override fun encodeByte(value: Byte) = sink.writeByte(value)
    override fun encodeBoolean(value: Boolean) = sink.writeByte(if (value) 0x01 else 0x00)
    override fun encodeChar(value: Char) = sink.writeInt(value.code)
    override fun encodeDouble(value: Double) = sink.writeDouble(value)
    override fun encodeFloat(value: Float) = sink.writeFloat(value)
    override fun encodeInt(value: Int) = sink.writeInt(value)
    override fun encodeLong(value: Long) = sink.writeLong(value)
    override fun encodeShort(value: Short) = sink.writeShort(value)
    override fun encodeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        sink.writeVarInt(bytes.size)

        sink.write(bytes, 0, bytes.size)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        val annotation = enumDescriptor.getAnnotation<EVariant>()

        val variant = annotation?.kind ?: NumVariant.VarInt // fallback to VarInt

        val value = enumDescriptor.getElementAnnotationFromIndex<EValue>(index)?.value ?: index

        NumVariant.encodeInt(variant, value, sink)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) = encodeByte(value)
    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) = encodeBoolean(value)
    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) = encodeChar(value)
    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) = encodeDouble(value)
    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) = encodeFloat(value)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarInt
        NumVariant.encodeInt(variant, value, sink)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarLong
        NumVariant.encodeLong(variant, value, sink)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) = encodeShort(value)
    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) = encodeString(value)

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this
    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder = this

    @ExperimentalSerializationApi
    override fun encodeNull() {
        error("Cannot encode null")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        encodeNullableSerializableValue(serializer, value)
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        encodeSerializableValue(serializer, value)
    }

    // Structure Methods
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {

    }
}