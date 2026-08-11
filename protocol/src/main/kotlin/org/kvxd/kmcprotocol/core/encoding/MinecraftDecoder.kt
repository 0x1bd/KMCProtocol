package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.KmcProtocolException

class MinecraftDecoder(
    private val reader: PacketReader,
    private val codec: ProtocolCodec = ProtocolCodec.Empty,
    override val serializersModule: SerializersModule = EmptySerializersModule(),
) : Decoder, CompositeDecoder {

    private val indexStack = mutableListOf<Int>()
    private val contextStack = mutableListOf<MutableMap<String, Any?>>()
    private val bitfieldStack = mutableListOf<LongArray>()
    private val arrayStack = mutableListOf<ArrayFrame?>()
    private val collectionStack = mutableListOf<Boolean>()
    private var pendingArrayLevel: ArrayLevel? = null
    private var pendingFlags: ProtocolFlags? = null
    private var pendingFlagsEncoding: NumberEncoding = NumberEncoding.VarInt

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (indexStack.isEmpty()) return CompositeDecoder.DECODE_DONE

        val currentIndex = indexStack.last()
        if (currentIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        indexStack[indexStack.lastIndex] = currentIndex + 1
        return currentIndex
    }

    override fun decodeByte(): Byte = reader.readByte()
    override fun decodeBoolean(): Boolean = reader.readByte().toInt() != 0
    override fun decodeChar(): Char = reader.readInt().toChar()
    override fun decodeDouble(): Double = reader.readDouble()
    override fun decodeFloat(): Float = reader.readFloat()
    override fun decodeInt(): Int = reader.readInt()
    override fun decodeLong(): Long = reader.readLong()
    override fun decodeShort(): Short = reader.readShort()
    override fun decodeString(): String = reader.readString()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val encoding = enumDescriptor.getAnnotation<ProtocolEnum>()?.encoding ?: NumberEncoding.VarInt
        val value = reader.readNumber(encoding).toInt()
        val indices = 0 until enumDescriptor.elementsCount

        val declared = indices.mapNotNull { enumDescriptor.getElementAnnotationFromIndex<ProtocolEnumValue>(it)?.value }
        if (declared.isEmpty()) {
            return value.takeIf { it in indices }
                ?: throw KmcProtocolException(
                    "Failed to decode enum ${enumDescriptor.serialName}: ordinal $value is out of bounds."
                )
        }

        return indices
            .singleOrNull { value == enumDescriptor.getElementAnnotationFromIndex<ProtocolEnumValue>(it)?.value }
            ?: throw KmcProtocolException(
                "Failed to decode enum ${enumDescriptor.serialName}: no entry declares protocol value $value."
            )
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        val encoding = elementEncoding(descriptor, index)
        val value = if (encoding == null) reader.readByte() else reader.readNumber(encoding).toByte()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        val encoding = elementEncoding(descriptor, index)
        val value = if (encoding == null) reader.readShort() else reader.readNumber(encoding).toShort()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        bitfieldValue(index)?.let { return it.toInt() }
        val value = reader.readNumber(elementEncoding(descriptor, index) ?: NumberEncoding.VarInt).toInt()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        bitfieldValue(index)?.let { return it }
        val value = reader.readNumber(elementEncoding(descriptor, index) ?: NumberEncoding.VarLong).toLong()
        recordElement(descriptor, index, value)
        return value
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
        decodeBoolean().also { recordElement(descriptor, index, it) }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
        decodeChar().also { recordElement(descriptor, index, it) }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        decodeDouble().also { recordElement(descriptor, index, it) }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        decodeFloat().also { recordElement(descriptor, index, it) }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        val value = if (protocolType == null) {
            decodeString()
        } else {
            codec.decodeText(codec.parse(protocolType.json), reader, currentContext())
        }
        recordElement(descriptor, index, value)
        return value
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val dynamic = codec.decodeAny(codec.parse(protocolType.json), deserializer, reader, currentContext())
            recordSerializableElement(descriptor, index, deserializer, dynamic)
            return dynamic
        }

        val binary = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
        if (descriptor.kind != StructureKind.LIST && binary != null &&
            descriptor.getElementAnnotationFromIndex<ProtocolArray>(index) == null
        ) {
            @Suppress("UNCHECKED_CAST")
            val direct = reader.readBinary(binary) as T
            recordSerializableElement(descriptor, index, deserializer, direct)
            return direct
        }
        if (descriptor.kind == StructureKind.LIST) {
            val frame = arrayStack.lastOrNull()
            val leaf = frame?.binary
            if (leaf != null) {
                @Suppress("UNCHECKED_CAST")
                return reader.readBinary(leaf) as T
            }
            if (frame != null && deserializer.descriptor.kind.isIntegral()) {
                val raw = reader.readNumber(frame.elementEncoding)
                @Suppress("UNCHECKED_CAST")
                return when (deserializer.descriptor.kind) {
                    kotlinx.serialization.descriptors.PrimitiveKind.INT -> raw.toInt()
                    kotlinx.serialization.descriptors.PrimitiveKind.LONG -> raw.toLong()
                    kotlinx.serialization.descriptors.PrimitiveKind.SHORT -> raw.toShort()
                    else -> raw.toByte()
                } as T
            }
        }

        if (descriptor.kind == StructureKind.LIST) {
            return decodeSerializableValue(deserializer)
        }

        descriptor.getElementAnnotationFromIndex<ProtocolFlags>(index)?.let {
            pendingFlags = it
            pendingFlagsEncoding = it.encoding
        }
        arrayStack.add(
            descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)?.let { ArrayFrame(it, binary) }
        )
        val value = try {
            decodeSerializableValue(deserializer)
        } finally {
            arrayStack.removeLastOrNull()
        }
        recordSerializableElement(descriptor, index, deserializer, value)
        return value
    }

    @ExperimentalSerializationApi
    override fun decodeSequentially(): Boolean = collectionStack.lastOrNull() == true

    @ExperimentalSerializationApi
    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val level = pendingArrayLevel ?: return reader.readVarInt()
        pendingArrayLevel = null
        if (level.fixedCount >= 0) return level.fixedCount
        if (level.countField.isNotEmpty()) {
            val referenced = contextStack.resolvePath(level.countField)
            return (referenced as? Number)?.toInt()
                ?: throw KmcProtocolException("Array count field '${level.countField}' is missing or not numeric.")
        }
        return reader.readNumber(level.countType).toInt()
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? {
        val binarySpec = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
        val switch = descriptor.getElementAnnotationFromIndex<ProtocolSwitch>(index)
        if (switch != null) {
            if (!switchBranchPresent(
                switch,
                contextStack.resolvePathOrUnresolved(switch.compareTo),
                "${descriptor.serialName}.${descriptor.getElementName(index)}",
            )) {
                recordSerializableElement(descriptor, index, deserializer, null)
                return null
            }
            val branch = readOptionValue(descriptor, index, deserializer, binarySpec)
            recordSerializableElement(descriptor, index, deserializer, branch)
            return branch
        }
        if (descriptor.getElementAnnotationFromIndex<ProtocolOption>(index) != null) {
            if (reader.readByte().toInt() == 0) {
                recordSerializableElement(descriptor, index, deserializer, null)
                return null
            }
            val present = readOptionValue(descriptor, index, deserializer, binarySpec)
            recordSerializableElement(descriptor, index, deserializer, present)
            return present
        }
        binarySpec?.let { binary ->
            @Suppress("UNCHECKED_CAST")
            val direct = reader.readBinary(binary) as T?
            recordSerializableElement(descriptor, index, deserializer, direct)
            return direct
        }

        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
            ?: throw KmcProtocolException(
                "Nullable property '${descriptor.getElementName(index)}' of ${descriptor.serialName} has no " +
                    "ProtocolType or ProtocolBinary annotation, so its absence has no wire representation."
            )
        val value = codec.decodeAny(codec.parse(protocolType.json), deserializer, reader, currentContext())
        recordSerializableElement(descriptor, index, deserializer, value)
        return value
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = true

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        indexStack.add(0)
        contextStack.add(mutableMapOf())
        val isCollection = descriptor.kind == StructureKind.LIST
        collectionStack.add(isCollection)
        if (isCollection) pendingArrayLevel = arrayStack.lastOrNull()?.enter() ?: ArrayLevel(NumberEncoding.VarInt, -1, "")
        descriptor.bitfieldLayout()?.let { bitfieldStack.add(it.unpack(reader.readSizedInteger(it.totalBits))) }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        indexStack.removeLastOrNull()
        contextStack.removeLastOrNull()
        if (collectionStack.removeLastOrNull() == true) arrayStack.lastOrNull()?.exit()
        if (descriptor.bitfieldLayout() != null) bitfieldStack.removeLastOrNull()
    }

    private fun bitfieldValue(index: Int): Long? =
        bitfieldStack.lastOrNull()?.takeIf { index in it.indices }?.get(index)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readOptionValue(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        binary: ProtocolBinary?,
    ): T? {
        if (binary != null) return reader.readBinary(binary) as T?
        val arraySpec = descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)
        if (arraySpec == null) {
            val encoding = descriptor.numberEncoding(index)
            if (encoding != null && deserializer.descriptor.kind.isIntegral()) {
                val raw = reader.readNumber(encoding)
                return when (deserializer.descriptor.kind) {
                    kotlinx.serialization.descriptors.PrimitiveKind.INT -> raw.toInt()
                    kotlinx.serialization.descriptors.PrimitiveKind.LONG -> raw.toLong()
                    kotlinx.serialization.descriptors.PrimitiveKind.SHORT -> raw.toShort()
                    else -> raw.toByte()
                } as T?
            }
            return decodeSerializableValue(deserializer)
        }
        arrayStack.add(ArrayFrame(arraySpec, null))
        return try {
            decodeSerializableValue(deserializer)
        } finally {
            arrayStack.removeLastOrNull()
        }
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        val flags = pendingFlags
        if (flags != null) {
            pendingFlags = null
            return readFlagSet(deserializer)
        }
        return super.decodeSerializableValue(deserializer)
    }

    private fun <T> readFlagSet(deserializer: DeserializationStrategy<T>): T {
        val mask = reader.readNumber(pendingFlagsEncoding).toInt()
        val enumDescriptor = deserializer.descriptor.getElementDescriptor(0)
        val selectedNames = (0 until enumDescriptor.elementsCount)
            .filter { mask and (1 shl it) != 0 }
            .map { index ->
                enumDescriptor.getElementAnnotations(index)
                    .filterIsInstance<kotlinx.serialization.SerialName>()
                    .firstOrNull()
                    ?.value
                    ?: enumDescriptor.getElementName(index)
            }
        return deserializer.deserialize(DynamicValueBackedDecoder(selectedNames))
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

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }

    private fun <T> recordSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        value: T,
    ) {
        recordElement(descriptor, index, value.toDecodedDynamicValue(deserializer))
    }
}
