package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import net.benwoodworth.knbt.NbtTag

internal class DynamicValueBackedDecoder(
    private val value: Any?,
) : Decoder, CompositeDecoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private val stack = mutableListOf<Frame>()
    private var currentValue: Any? = value

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val frameValue = currentValue.unwrap()
        stack += when (frameValue) {
            is List<*> -> Frame.ListFrame(frameValue)
            is ByteArray -> Frame.ListFrame(frameValue.toList())
            is Map<*, *> -> Frame.ObjectFrame(frameValue.mapKeys { it.key.toString() })
            else -> Frame.ObjectFrame(emptyMap())
        }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        stack.removeAt(stack.lastIndex)
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val frame = stack.lastOrNull() ?: return CompositeDecoder.DECODE_DONE
        val index = frame.nextIndex(descriptor.elementsCount)
        if (index == CompositeDecoder.DECODE_DONE) return index

        currentValue = when (frame) {
            is Frame.ListFrame -> frame.values.getOrNull(index)
            is Frame.ObjectFrame -> frame.values[descriptor.getElementName(index)]
        }.unwrap()
        return index
    }

    @ExperimentalSerializationApi
    override fun decodeSequentially(): Boolean = false

    @ExperimentalSerializationApi
    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        (stack.lastOrNull() as? Frame.ListFrame)?.values?.size ?: descriptor.elementsCount

    override fun decodeBoolean(): Boolean = currentValue.unwrap() as? Boolean ?: false
    override fun decodeByte(): Byte = (currentValue.unwrap() as? Number)?.toByte() ?: 0
    override fun decodeChar(): Char = (currentValue.unwrap() as? Char) ?: 0.toChar()
    override fun decodeDouble(): Double = (currentValue.unwrap() as? Number)?.toDouble() ?: 0.0
    override fun decodeFloat(): Float = (currentValue.unwrap() as? Number)?.toFloat() ?: 0f
    override fun decodeInt(): Int = (currentValue.unwrap() as? Number)?.toInt() ?: 0
    override fun decodeLong(): Long = (currentValue.unwrap() as? Number)?.toLong() ?: 0L
    override fun decodeShort(): Short = (currentValue.unwrap() as? Number)?.toShort() ?: 0
    override fun decodeString(): String = currentValue.unwrap()?.toString() ?: ""
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val name = currentValue.unwrap()?.toString() ?: return 0
        return (0 until enumDescriptor.elementsCount)
            .firstOrNull {
                enumDescriptor.getElementName(it) == name ||
                    enumDescriptor.getElementAnnotations(it)
                        .filterIsInstance<SerialName>()
                        .firstOrNull()
                        ?.value == name
            }
            ?: 0
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = decodeBoolean()
    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = decodeByte()
    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = decodeChar()
    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = decodeDouble()
    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = decodeFloat()
    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = decodeInt()
    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = decodeLong()
    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = decodeShort()
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = decodeString()

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        val unwrapped = currentValue.unwrap()
        if (unwrapped is NbtTag && deserializer.descriptor.serialName.startsWith("net.benwoodworth.knbt.")) {
            @Suppress("UNCHECKED_CAST")
            return unwrapped as T
        }
        return deserializer.deserialize(this)
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T = decodeSerializableValue(deserializer)

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? = if (currentValue == null) null else decodeSerializableValue(deserializer)

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = currentValue != null

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    private fun Any?.unwrap(): Any? = if (this is DynamicValue) value else this

    private sealed class Frame {
        var index: Int = 0

        open fun nextIndex(size: Int): Int {
            if (index >= size) return CompositeDecoder.DECODE_DONE
            return index++
        }

        class ListFrame(val values: List<*>) : Frame() {
            override fun nextIndex(size: Int): Int {
                if (index >= values.size) return CompositeDecoder.DECODE_DONE
                return index++
            }
        }
        class ObjectFrame(val values: Map<String, Any?>) : Frame()
    }
}
