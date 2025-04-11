package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.variant.*
import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readDouble
import kotlinx.io.readFloat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

class MinecraftDecoder(data: ProtocolData, private val source: Source) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = data.serializersModule
    private val indexStack = mutableListOf<Int>()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (indexStack.isEmpty()) return CompositeDecoder.DECODE_DONE

        val currentIndex = indexStack.last()

        if (currentIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        indexStack[indexStack.lastIndex] = currentIndex + 1

        return currentIndex
    }

    override fun decodeByte(): Byte = source.readByte()
    override fun decodeBoolean(): Boolean = source.readByte().toInt() == 0x01
    override fun decodeChar(): Char = source.readInt().toChar()
    override fun decodeDouble(): Double = source.readDouble()
    override fun decodeFloat(): Float = source.readFloat()
    override fun decodeInt(): Int = source.readInt()
    override fun decodeLong(): Long = source.readLong()
    override fun decodeShort(): Short = source.readShort()
    override fun decodeString(): String {
        val length = source.readVarInt()

        val bytes = ByteArray(length)
        source.readFully(bytes, 0, length)

        return String(bytes, Charsets.UTF_8)
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val variant = enumDescriptor.getAnnotation<EVariant>()?.kind ?: NumVariant.VarInt

        val value = NumVariant.decodeInt(variant, source)

        return enumDescriptor.elementDescriptors
            .withIndex()
            .singleOrNull { (i) ->
                value == enumDescriptor
                    .getElementAnnotationFromIndex<EValue>(i)
                    ?.value
            }
            ?.index
            ?: error("Failed to decode enum ${enumDescriptor.serialName}. Value index $value does not exist.")
    }


    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = decodeByte()
    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = decodeBoolean()
    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = decodeChar()
    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = decodeDouble()
    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = decodeFloat()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarInt
        return NumVariant.decodeInt(variant, source)
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        val annotation = descriptor.getElementAnnotationFromIndex<NV>(index)

        val variant = annotation?.kind ?: NumVariant.VarLong
        return NumVariant.decodeLong(variant, source)
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = decodeShort()
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = decodeString()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        error("Cannot decode null")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        indexStack.add(0)
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
    ): T =
        decodeSerializableValue(deserializer)

    override fun endStructure(descriptor: SerialDescriptor) {
        if (indexStack.isNotEmpty())
            indexStack.removeAt(indexStack.lastIndex)
    }
}