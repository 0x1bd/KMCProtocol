package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.variant.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

class MinecraftEncoder(private val data: ProtocolData, private val channel: ByteWriteChannel) : Encoder,
    CompositeEncoder {

    override val serializersModule: SerializersModule = data.serializersModule

    // Ensures sequential write ops
    private val writeMutex = Mutex()

    private inline fun <T> write(crossinline block: suspend () -> T): T {
        return runBlocking {
            writeMutex.withLock { block() }
        }
    }

    override fun encodeByte(value: Byte) = write { channel.writeByte(value) }
    override fun encodeBoolean(value: Boolean) = write { channel.writeByte(if (value) 0x01 else 0x00) }
    override fun encodeChar(value: Char) = write { channel.writeInt(value.code) }
    override fun encodeDouble(value: Double) = write { channel.writeDouble(value) }
    override fun encodeFloat(value: Float) = write { channel.writeFloat(value) }
    override fun encodeInt(value: Int) = write { channel.writeInt(value) }
    override fun encodeLong(value: Long) = write { channel.writeLong(value) }
    override fun encodeShort(value: Short) = write { channel.writeShort(value) }
    override fun encodeString(value: String) = write {
        val bytes = value.toByteArray(Charsets.UTF_8)
        channel.writeVarInt(bytes.size)

        channel.writeFully(bytes, 0, bytes.size)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = write {
        val annotation = enumDescriptor.getAnnotation<EVariant>()

        val variant = annotation?.kind ?: NumVariant.VarInt // fallback to VarInt

        val value = enumDescriptor.getElementAnnotationFromIndex<EValue>(index)?.value ?: index

        NumVariant.encodeInt(variant, value, channel)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) = encodeByte(value)
    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) = encodeBoolean(value)
    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) = encodeChar(value)
    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) = encodeDouble(value)
    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) = encodeFloat(value)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) = write {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarInt
        NumVariant.encodeInt(variant, value, channel)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) = write {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarLong
        NumVariant.encodeLong(variant, value, channel)
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
        when {
            descriptor.getElementAnnotations(index).filterIsInstance<UseJson>().isNotEmpty() ->
                encodeString(data.json.encodeToString(serializer, value))

            else -> encodeSerializableValue(serializer, value)
        }
    }

    // Structure Methods
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {

    }
}