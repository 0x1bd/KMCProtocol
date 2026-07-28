package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.KmcProtocolException

class MinecraftEncoder(
    private val writer: PacketWriter,
    private val codec: ProtocolCodec = ProtocolCodec.Empty,
    override val serializersModule: SerializersModule = EmptySerializersModule(),
) : Encoder, CompositeEncoder {

    private val contextStack = mutableListOf<MutableMap<String, Any?>>()
    private val bitfieldStack = mutableListOf<BitfieldFrame>()
    private val arrayStack = mutableListOf<ArrayFrame?>()
    private val collectionStack = mutableListOf<Boolean>()
    private var pendingFlags: ProtocolFlags? = null

    private class BitfieldFrame(val layout: BitfieldLayout) {
        val values = LongArray(layout.sizes.size)
    }

    override fun encodeByte(value: Byte) = writer.writeByte(value)
    override fun encodeBoolean(value: Boolean) = writer.writeByte(if (value) 1 else 0)
    override fun encodeChar(value: Char) = writer.writeInt(value.code)
    override fun encodeDouble(value: Double) = writer.writeDouble(value)
    override fun encodeFloat(value: Float) = writer.writeFloat(value)
    override fun encodeInt(value: Int) = writer.writeInt(value)
    override fun encodeLong(value: Long) = writer.writeLong(value)
    override fun encodeShort(value: Short) = writer.writeShort(value)
    override fun encodeString(value: String) = writer.writeString(value)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        val encoding = enumDescriptor.getAnnotation<ProtocolEnum>()?.encoding ?: NumberEncoding.VarInt
        val value = enumDescriptor.getElementAnnotationFromIndex<ProtocolEnumValue>(index)?.value ?: index
        writer.writeNumber(encoding, value)
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        if (collectBitfield(index, value.toLong())) return
        writer.writeNumber(elementEncoding(descriptor, index) ?: NumberEncoding.VarInt, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        if (collectBitfield(index, value)) return
        writer.writeNumber(elementEncoding(descriptor, index) ?: NumberEncoding.VarLong, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        val encoding = elementEncoding(descriptor, index)
        if (encoding == null) writer.writeByte(value) else writer.writeNumber(encoding, value)
        recordElement(descriptor, index, value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        val encoding = elementEncoding(descriptor, index)
        if (encoding == null) writer.writeShort(value) else writer.writeNumber(encoding, value)
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

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        if (!encodeDynamicElement(descriptor, index, value)) encodeString(value)
        recordElement(descriptor, index, value)
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        if (encodeDynamicElement(descriptor, index, value)) {
            recordElement(descriptor, index, value)
            return
        }

        val binary = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
        if (descriptor.kind != StructureKind.LIST && binary != null &&
            descriptor.getElementAnnotationFromIndex<ProtocolArray>(index) == null
        ) {
            writer.writeBinary(binary, value)
            recordElement(descriptor, index, value)
            return
        }
        if (descriptor.kind == StructureKind.LIST) {
            val frame = arrayStack.lastOrNull()
            val leaf = frame?.binary
            if (leaf != null) {
                writer.writeBinary(leaf, value)
                return
            }
            if (frame != null && value is Number && serializer.descriptor.kind.isIntegral()) {
                writer.writeNumber(frame.elementEncoding, value)
                return
            }
        }

        if (descriptor.kind == StructureKind.LIST) {
            encodeSerializableValue(serializer, value)
            return
        }

        pendingFlags = descriptor.getElementAnnotationFromIndex<ProtocolFlags>(index)
        arrayStack.add(
            descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)?.let { ArrayFrame(it, binary) }
        )
        try {
            encodeSerializableValue(serializer, value)
        } finally {
            arrayStack.removeLastOrNull()
        }
        recordElement(descriptor, index, value)
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        val level = arrayStack.lastOrNull()?.enter()
        if (level == null || level.writesCount) {
            writer.writeNumber(level?.countType ?: NumberEncoding.VarInt, collectionSize)
        } else if (level.fixedCount >= 0 && level.fixedCount != collectionSize) {
            throw KmcProtocolException(
                "Array declares a fixed length of ${level.fixedCount} but the value has $collectionSize elements."
            )
        } else if (level.countField.isNotEmpty()) {
            val declared = (contextStack.resolvePath(level.countField) as? Number)?.toInt()
            if (declared != null && declared != collectionSize) {
                throw KmcProtocolException(
                    "Field '${level.countField}' declares $declared elements but the array has $collectionSize."
                )
            }
        }
        return beginStructure(descriptor)
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        if (encodeDynamicElement(descriptor, index, value)) {
            recordElement(descriptor, index, value)
            return
        }
        val binary = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
        val switch = descriptor.getElementAnnotationFromIndex<ProtocolSwitch>(index)
        if (switch != null) {
            val present = switchBranchPresent(switch, contextStack.resolvePath(switch.compareTo))
            if (present && value == null) {
                throw KmcProtocolException(
                    "Property '${descriptor.getElementName(index)}' of ${descriptor.serialName} is required when " +
                        "'${switch.compareTo}' selects a present branch, but it was null."
                )
            }
            if (!present && value != null) {
                throw KmcProtocolException(
                    "Property '${descriptor.getElementName(index)}' of ${descriptor.serialName} must be null when " +
                        "'${switch.compareTo}' selects no branch."
                )
            }
            if (present) writeOptionValue(descriptor, index, serializer, value!!, binary)
            recordElement(descriptor, index, value)
            return
        }
        if (descriptor.getElementAnnotationFromIndex<ProtocolOption>(index) != null) {
            writer.writeByte(if (value == null) 0 else 1)
            if (value != null) writeOptionValue(descriptor, index, serializer, value, binary)
            recordElement(descriptor, index, value)
            return
        }
        if (binary != null) {
            writer.writeBinary(binary, value)
            recordElement(descriptor, index, value)
            return
        }
        encodeNullableSerializableValue(serializer, value)
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this
    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder = this

    @ExperimentalSerializationApi
    override fun encodeNull() {
        throw KmcProtocolException("Minecraft packets cannot encode a bare null; use a dynamic protocol type instead.")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        contextStack.add(mutableMapOf())
        collectionStack.add(descriptor.kind == StructureKind.LIST)
        descriptor.bitfieldLayout()?.let { bitfieldStack.add(BitfieldFrame(it)) }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        contextStack.removeLastOrNull()
        if (collectionStack.removeLastOrNull() == true) arrayStack.lastOrNull()?.exit()
        if (descriptor.bitfieldLayout() != null) {
            val frame = bitfieldStack.removeLastOrNull() ?: return
            writer.writeSizedInteger(frame.layout.pack(frame.values), frame.layout.totalBits)
        }
    }

    private fun collectBitfield(index: Int, value: Long): Boolean {
        val frame = bitfieldStack.lastOrNull() ?: return false
        if (index !in frame.values.indices) return false
        frame.values[index] = value
        return true
    }

    private fun encodeDynamicElement(descriptor: SerialDescriptor, index: Int, value: Any?): Boolean {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index) ?: return false
        codec.encodeAny(codec.parse(protocolType.json), value, writer, currentContext())
        return true
    }

    private fun <T : Any> writeOptionValue(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
        binary: ProtocolBinary?,
    ) {
        if (binary != null) {
            writer.writeBinary(binary, value)
            return
        }
        val arraySpec = descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)
        if (arraySpec == null) {
            val encoding = descriptor.numberEncoding(index)
            if (encoding != null && value is Number && serializer.descriptor.kind.isIntegral()) {
                writer.writeNumber(encoding, value)
                return
            }
            encodeSerializableValue(serializer, value)
            return
        }
        arrayStack.add(ArrayFrame(arraySpec, null))
        try {
            encodeSerializableValue(serializer, value)
        } finally {
            arrayStack.removeLastOrNull()
        }
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        val flags = pendingFlags
        if (flags != null) {
            pendingFlags = null
            writer.writeNumber(flags.encoding, flagMask(value))
            return
        }
        super.encodeSerializableValue(serializer, value)
    }

    private fun flagMask(value: Any?): Int {
        val entries = (value as? Iterable<*>) ?: emptyList<Any?>()
        var mask = 0
        entries.forEach { entry -> if (entry is Enum<*>) mask = mask or (1 shl entry.ordinal) }
        return mask
    }

    private fun kotlinx.serialization.descriptors.SerialKind.isIntegral(): Boolean =
        this == kotlinx.serialization.descriptors.PrimitiveKind.INT ||
            this == kotlinx.serialization.descriptors.PrimitiveKind.LONG ||
            this == kotlinx.serialization.descriptors.PrimitiveKind.SHORT ||
            this == kotlinx.serialization.descriptors.PrimitiveKind.BYTE

    private fun elementEncoding(descriptor: SerialDescriptor, index: Int): NumberEncoding? =
        if (descriptor.kind == StructureKind.LIST) arrayStack.lastOrNull()?.elementEncoding
        else descriptor.numberEncoding(index)

    private fun currentContext(): MutableMap<String, Any?> = contextStack.lastOrNull() ?: mutableMapOf()

    internal fun contextValue(path: String): Any? = contextStack.resolvePath(path)

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }
}

internal fun SerialDescriptor.numberEncoding(index: Int): NumberEncoding? =
    getElementAnnotationFromIndex<ProtocolNumber>(index)?.encoding
