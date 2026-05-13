package org.kvxd.kmcprotocol.core.encoding

import org.kvxd.kmcprotocol.core.ProtocolData
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

class MinecraftDecoder(data: ProtocolData, private val channel: ByteReadChannel) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = data.serializersModule
    private val indexStack = mutableListOf<Int>()
    private val contextStack = mutableListOf<MutableMap<String, Any?>>()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (indexStack.isEmpty()) return CompositeDecoder.DECODE_DONE

        val currentIndex = indexStack.last()

        if (currentIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        indexStack[indexStack.lastIndex] = currentIndex + 1

        return currentIndex
    }

    override fun decodeByte(): Byte = runBlocking { channel.readByte() }
    override fun decodeBoolean(): Boolean = runBlocking { channel.readByte().toInt() == 0x01 }
    override fun decodeChar(): Char = runBlocking { channel.readInt().toChar() }
    override fun decodeDouble(): Double = runBlocking { channel.readDouble() }
    override fun decodeFloat(): Float = runBlocking { channel.readFloat() }
    override fun decodeInt(): Int = runBlocking { channel.readInt() }
    override fun decodeLong(): Long = runBlocking { channel.readLong() }
    override fun decodeShort(): Short = runBlocking { channel.readShort() }
    override fun decodeString(): String = runBlocking {
        val length = channel.readVarInt()

        val bytes = ByteArray(length)
        channel.readFully(bytes, 0, length)

        return@runBlocking String(bytes, Charsets.UTF_8)
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = runBlocking {
        val encoding = enumDescriptor.getAnnotation<ProtocolEnum>()?.encoding ?: NumberEncoding.VarInt
        val value = (channel.readNumber(encoding) as Number).toInt()

        return@runBlocking enumDescriptor.elementDescriptors
            .withIndex()
            .singleOrNull { (i) ->
                value == enumDescriptor
                    .getElementAnnotationFromIndex<ProtocolEnumValue>(i)
                    ?.value
            }
            ?.index
            ?: error("Failed to decode enum ${enumDescriptor.serialName}. Value index $value does not exist.")
    }


    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        val value = runBlocking {
            descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)
                ?.let { channel.readNumber(it.encoding).toByte() }
                ?: channel.readByte()
        }
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val value = decodeBoolean()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
        val value = decodeChar()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
        val value = decodeDouble()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
        val value = decodeFloat()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = runBlocking {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding ?: NumberEncoding.VarInt
        val value = channel.readNumber(encoding).toInt()
        recordElement(descriptor, index, value)
        return@runBlocking value
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = runBlocking {
        val encoding = descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding ?: NumberEncoding.VarLong
        val value = channel.readNumber(encoding).toLong()
        recordElement(descriptor, index, value)
        return@runBlocking value
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        val value = runBlocking {
            descriptor.getElementAnnotationFromIndex<ProtocolNumber>(index)
                ?.let { channel.readNumber(it.encoding).toShort() }
                ?: channel.readShort()
        }
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        val value = decodeString()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        error("Cannot decode null")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        indexStack.add(0)
        contextStack.add(mutableMapOf())
        return this
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        error("Cannot decode null")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        error("Cannot decode null")
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            @Suppress("UNCHECKED_CAST")
            val value = runBlocking {
                val context = contextStack.lastOrNull() ?: mutableMapOf()
                ProtocolDynamicCodec.decode(ProtocolDynamicCodec.parse(protocolType.json), channel, context)
            } as T
            recordElement(descriptor, index, (value as ProtocolValue).value)
            return value
        }

        val value = decodeSerializableValue(deserializer)
        recordElement(descriptor, index, value)
        return value
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (indexStack.isNotEmpty())
            indexStack.removeAt(indexStack.lastIndex)
        if (contextStack.isNotEmpty())
            contextStack.removeAt(contextStack.lastIndex)
    }

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }
}
