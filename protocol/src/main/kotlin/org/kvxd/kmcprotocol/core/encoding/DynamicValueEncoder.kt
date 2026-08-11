package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

internal fun <T> encodeDynamicValue(serializer: SerializationStrategy<T>, value: T): DynamicValue {
    var result = DynamicValue.Null
    serializer.serialize(DynamicValueEncoder { result = it }, value)
    return result
}

private class DynamicValueEncoder(
    private val emit: (DynamicValue) -> Unit,
) : Encoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        DynamicValueCompositeEncoder(descriptor, emit)

    override fun encodeBoolean(value: Boolean) = emit(DynamicValue(value))
    override fun encodeByte(value: Byte) = emit(DynamicValue(value))
    override fun encodeChar(value: Char) = emit(DynamicValue(value))
    override fun encodeDouble(value: Double) = emit(DynamicValue(value))
    override fun encodeFloat(value: Float) = emit(DynamicValue(value))
    override fun encodeInt(value: Int) = emit(DynamicValue(value))
    override fun encodeLong(value: Long) = emit(DynamicValue(value))
    override fun encodeShort(value: Short) = emit(DynamicValue(value))
    override fun encodeString(value: String) = emit(DynamicValue(value))

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        val serialName = enumDescriptor.getElementAnnotations(index)
            .filterIsInstance<kotlinx.serialization.SerialName>()
            .firstOrNull()
            ?.value
            ?: enumDescriptor.getElementName(index)
        emit(DynamicValue(serialName))
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    @ExperimentalSerializationApi
    override fun encodeNull() = emit(DynamicValue.Null)
}

private class DynamicValueCompositeEncoder(
    private val descriptor: SerialDescriptor,
    private val emit: (DynamicValue) -> Unit,
) : CompositeEncoder {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private val values = linkedMapOf<Int, DynamicValue>()

    override fun endStructure(descriptor: SerialDescriptor) {
        emit(
            when (this.descriptor.kind) {
                StructureKind.LIST -> DynamicValue.list(values.toSortedMap().values.toList())
                StructureKind.MAP -> DynamicValue.obj(
                    values.toSortedMap().values.chunked(2).associate { entry ->
                        val key = entry.firstOrNull()?.value?.toString().orEmpty()
                        key to entry.getOrElse(1) { DynamicValue.Null }
                    }
                )
                else -> DynamicValue.obj(
                    values.mapKeys { (index) -> this.descriptor.getElementName(index) }
                )
            }
        )
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) = put(index, value)
    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) = put(index, value)
    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) = put(index, value)
    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) = put(index, value)
    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) = put(index, value)
    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) = put(index, value)
    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) = put(index, value)
    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) = put(index, value)
    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) = put(index, value)

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        values[index] = value.toDynamicValue(serializer)
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        values[index] = if (value == null) DynamicValue.Null else value.toDynamicValue(serializer)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder =
        DynamicValueEncoder { values[index] = it }

    @ExperimentalSerializationApi
    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = true

    private fun put(index: Int, value: Any?) {
        values[index] = DynamicValue(value)
    }
}
