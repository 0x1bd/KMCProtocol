package org.kvxd.kmcprotocol.core.encoding

import org.kvxd.kmcprotocol.core.ProtocolData
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

class MinecraftEncoder(data: ProtocolData, private val channel: ByteWriteChannel) : Encoder, CompositeEncoder {

    override val serializersModule: SerializersModule = data.serializersModule
    private val contextStack = mutableListOf<MutableMap<String, Any?>>()

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
        val encoding = enumDescriptor.getAnnotation<ProtocolEnum>()?.encoding ?: NumberEncoding.VarInt
        val value = enumDescriptor.getElementAnnotationFromIndex<ProtocolEnumValue>(index)?.value ?: index
        channel.writeNumber(encoding, value)
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) = write {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding ?: NumberEncoding.VarInt
        channel.writeNumber(encoding, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) = write {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding ?: NumberEncoding.VarLong
        channel.writeNumber(encoding, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) = write {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding
        if (encoding == null) channel.writeByte(value) else channel.writeNumber(encoding, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        encodeBoolean(value)
        recordElement(descriptor, index, value)
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        encodeChar(value)
        recordElement(descriptor, index, value)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        encodeDouble(value)
        recordElement(descriptor, index, value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        encodeFloat(value)
        recordElement(descriptor, index, value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) = write {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding
        if (encoding == null) channel.writeShort(value) else channel.writeNumber(encoding, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        encodeString(value)
        recordElement(descriptor, index, value)
    }

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
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null && value is ProtocolValue) {
            val context = contextStack.lastOrNull() ?: mutableMapOf()
            write {
                ProtocolDynamicCodec.encode(ProtocolDynamicCodec.parse(protocolType.json), value, channel, context)
            }
            recordElement(descriptor, index, value.value)
            return
        }

        encodeSerializableValue(serializer, value)
        recordElement(descriptor, index, value)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        contextStack.add(mutableMapOf())
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (contextStack.isNotEmpty()) {
            contextStack.removeAt(contextStack.lastIndex)
        }
    }

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }
}
