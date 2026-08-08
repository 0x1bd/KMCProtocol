package org.kvxd.kmcprotocol

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.ProtocolVersion
import org.kvxd.kmcprotocol.core.encoding.ProtocolCodec
import org.kvxd.kmcprotocol.core.encoding.BinaryKind
import org.kvxd.kmcprotocol.core.encoding.ProtocolArray
import org.kvxd.kmcprotocol.core.encoding.ProtocolBinary
import org.kvxd.kmcprotocol.core.encoding.ProtocolSwitch
import org.kvxd.kmcprotocol.core.encoding.resolvePath
import org.kvxd.kmcprotocol.core.encoding.resolvePathOrUnresolved
import org.kvxd.kmcprotocol.core.encoding.switchBranchPresent
import org.kvxd.kmcprotocol.core.encoding.ProtocolType
import org.kvxd.kmcprotocol.core.encoding.getElementAnnotationFromIndex
import org.kvxd.kmcprotocol.generated.v1_21_4.GeneratedPacketRegistry
import org.kvxd.kmcprotocol.generated.Protocols
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketSerializationTest {

    @Test
    fun `every generated version round trips every one of its packets`() = runBlocking {
        val failures = mutableListOf<String>()
        val perVersionCounts = Protocols.versions.associateWith { version ->
            roundTripVersion(version, failures)
        }

        assertTrue(Protocols.versions.isNotEmpty(), "Expected the catalog to contain generated versions")
        perVersionCounts.forEach { (version, count) ->
            assertTrue(count > 0, "Expected ${version.minecraftVersion} to contribute packets")
        }
        assertTrue(
            failures.isEmpty(),
            "Expected every generated packet sample to round trip; ${failures.size} failures, " +
                "first 10=${failures.take(10)}",
        )
    }

    private suspend fun roundTripVersion(version: ProtocolVersion, failures: MutableList<String>): Int {
        val data = version.protocolData()
        var roundTripped = 0

        ProtocolState.entries.forEach { state ->
            data.switchTo(state)

            version.registrySource.packetsById(state).values.flatMap { it.values }.forEach { packetClass ->
                val metadata = packetClass.findAnnotation<PacketMetadata>()
                assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")

                val serializer = data.registry.getPacketSerializerByClass(state, packetClass)
                assertNotNull(serializer, "${packetClass.qualifiedName} is not registered")

                val packet = runCatching { sampleGeneratedPacket(serializer, version) }
                    .getOrElse { error ->
                        failures += "${version.minecraftVersion} ${packetClass.qualifiedName}: sampling " +
                            "${error::class.simpleName}: ${error.message}"
                        return@forEach
                    }

                val channel = ByteChannel()
                data.send(packet, channel)
                channel.close()
                val encoded = channel.readRemaining().readByteArray()

                runCatching {
                    data.receive(ByteReadChannel(encoded), metadata.direction)
                }.onSuccess { decoded ->
                    if (decoded == null) {
                        failures += "${version.minecraftVersion} ${packetClass.qualifiedName}: decoded to null"
                    } else if (!packet.protocolEquals(decoded)) {
                        failures += "${version.minecraftVersion} ${packetClass.qualifiedName}: " +
                            "expected=$packet actual=$decoded"
                    } else {
                        roundTripped += 1
                    }
                }.onFailure { error ->
                    failures += "${version.minecraftVersion} ${packetClass.qualifiedName}: " +
                        "${error::class.simpleName}: ${error.message}"
                }
            }
        }

        return roundTripped
    }
}

internal fun sampleGeneratedPacket(
    serializer: KSerializer<out MinecraftPacket>,
    version: ProtocolVersion,
): MinecraftPacket = SamplePacketDecoder(version.codec).decode(serializer)

internal fun Any?.protocolEquals(other: Any?): Boolean {
    if (this === other || this == other) return true
    if (this is ByteArray && other is ByteArray) return contentEquals(other)
    if (this is List<*> && other is List<*>) {
        return size == other.size && zip(other).all { (left, right) -> left.protocolEquals(right) }
    }
    if (this is Set<*> && other is Set<*>) {
        return size == other.size && all { left -> other.any { right -> left.protocolEquals(right) } }
    }
    if (this is Map<*, *> && other is Map<*, *>) {
        return keys == other.keys && keys.all { key -> this[key].protocolEquals(other[key]) }
    }
    if (this != null && other != null && this::class == other::class) {
        if (this::class.java.isEnum) return this == other
        if (!this::class.isData) return false
        val properties = this::class.members
            .filterIsInstance<kotlin.reflect.KProperty1<Any, *>>()
            .filter { it.parameters.size == 1 }
        if (properties.isNotEmpty()) {
            return properties.all { property ->
                property.get(this).protocolEquals(property.get(other))
            }
        }
    }
    return this == other
}

internal class SamplePacketDecoder(private val codec: ProtocolCodec) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()
    private val indexStack = mutableListOf<Int>()
    private val contextStack = mutableListOf<MutableMap<String, Any?>>()
    private val arrayStack = mutableListOf<org.kvxd.kmcprotocol.core.encoding.ArrayFrame?>()
    private var pendingLevel: org.kvxd.kmcprotocol.core.encoding.ArrayLevel? = null
    private val collectionStack = mutableListOf<Boolean>()

    fun <T> decode(deserializer: DeserializationStrategy<T>): T =
        decodeSerializableValue(deserializer)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (indexStack.isEmpty()) return CompositeDecoder.DECODE_DONE

        val currentIndex = indexStack.last()
        if (currentIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        indexStack[indexStack.lastIndex] = currentIndex + 1
        return currentIndex
    }

    override fun decodeBoolean(): Boolean = true
    override fun decodeByte(): Byte = 0
    override fun decodeChar(): Char = 'x'
    override fun decodeDouble(): Double = 0.0
    override fun decodeFloat(): Float = 0.0f
    override fun decodeInt(): Int = 0
    override fun decodeLong(): Long = 0L
    override fun decodeShort(): Short = 0
    override fun decodeString(): String = "minecraft:sample"
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = 0

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        indexStack.add(0)
        contextStack.add(mutableMapOf())
        val isCollection = descriptor.kind == kotlinx.serialization.descriptors.StructureKind.LIST
        collectionStack.add(isCollection)
        if (isCollection) pendingLevel = arrayStack.lastOrNull()?.enter()
        return this
    }

    @ExperimentalSerializationApi
    override fun decodeSequentially(): Boolean = collectionStack.lastOrNull() == true

    @ExperimentalSerializationApi
    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val level = pendingLevel
        pendingLevel = null
        if (level == null) return 1
        if (level.fixedCount >= 0) return level.fixedCount
        if (level.countField.isNotEmpty()) {
            return (contextStack.resolvePath(level.countField) as? Number)?.toInt() ?: 0
        }
        return 1
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        indexStack.removeAt(indexStack.lastIndex)
        contextStack.removeAt(contextStack.lastIndex)
        if (collectionStack.removeLastOrNull() == true) arrayStack.lastOrNull()?.exit()
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
        decodeBoolean().also { recordElement(descriptor, index, it) }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte =
        decodeByte().also { recordElement(descriptor, index, it) }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
        decodeChar().also { recordElement(descriptor, index, it) }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        decodeDouble().also { recordElement(descriptor, index, it) }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        decodeFloat().also { recordElement(descriptor, index, it) }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        decodeInt().also { recordElement(descriptor, index, it) }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
        decodeLong().also { recordElement(descriptor, index, it) }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short =
        decodeShort().also { recordElement(descriptor, index, it) }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val value = codec.sampleText(codec.parse(protocolType.json), contextStack.lastOrNull() ?: mutableMapOf())
            recordElement(descriptor, index, value)
            return value
        }

        return decodeString().also { recordElement(descriptor, index, it) }
    }
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        val name = deserializer.descriptor.serialName
        if (name == "java.util.UUID") return java.util.UUID(0L, 1L) as T
        if (name == "kotlin.ByteArray") return ByteArray(0) as T
        if (name.startsWith("net.lenni0451.mcstructs.nbt.")) return net.lenni0451.mcstructs.nbt.tags.CompoundTag() as T
        return deserializer.deserialize(this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T {
        val arraySpec = descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)
        val binary = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
        if (arraySpec == null && binary != null) {
            val sampled = sampleBinary(binary) as T
            recordElement(descriptor, index, sampled)
            return sampled
        }
        if (descriptor.kind == kotlinx.serialization.descriptors.StructureKind.LIST) {
            arrayStack.lastOrNull()?.binary?.let { return sampleBinary(it) as T }
        }
        if (descriptor.kind == kotlinx.serialization.descriptors.StructureKind.LIST) {
            return decodeSerializableValue(deserializer)
        }
        if (arraySpec != null) {
            arrayStack.add(org.kvxd.kmcprotocol.core.encoding.ArrayFrame(arraySpec, binary))
            val sampled = try {
                decodeSerializableValue(deserializer)
            } finally {
                arrayStack.removeAt(arrayStack.lastIndex)
            }
            recordElement(descriptor, index, sampled)
            return sampled
        }

        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val value = codec.sampleAny(
                codec.parse(protocolType.json),
                deserializer,
                contextStack.lastOrNull() ?: mutableMapOf(),
            )
            recordElement(descriptor, index, value)
            return value
        }

        val value = decodeSerializableValue(deserializer)
        recordElement(descriptor, index, value)
        return value
    }

    @Suppress("UNCHECKED_CAST")
    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? {
        descriptor.getElementAnnotationFromIndex<ProtocolSwitch>(index)?.let { switch ->
            if (!switchBranchPresent(
                    switch,
                    contextStack.resolvePathOrUnresolved(switch.compareTo),
                    "${descriptor.serialName}.${descriptor.getElementName(index)}",
                )) {
                recordElement(descriptor, index, null)
                return null
            }
        }
        descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)?.let { binary ->
            if (descriptor.getElementAnnotationFromIndex<ProtocolArray>(index) == null) {
                return sampleBinary(binary) as T?
            }
        }
        descriptor.getElementAnnotationFromIndex<ProtocolArray>(index)?.let { arraySpec ->
            val binary = descriptor.getElementAnnotationFromIndex<ProtocolBinary>(index)
            arrayStack.add(org.kvxd.kmcprotocol.core.encoding.ArrayFrame(arraySpec, binary))
            val sampled = try {
                decodeSerializableValue(deserializer)
            } finally {
                arrayStack.removeAt(arrayStack.lastIndex)
            }
            recordElement(descriptor, index, sampled)
            return sampled
        }
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val value = codec.sampleAny(
                codec.parse(protocolType.json),
                deserializer,
                contextStack.lastOrNull() ?: mutableMapOf(),
            )
            recordElement(descriptor, index, value)
            return value
        }

        val sampled = decodeSerializableValue(deserializer)
        recordElement(descriptor, index, sampled)
        return sampled
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = true

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        throw SerializationException("Generated packet samples do not use null values")
    }

    private fun sampleBinary(spec: ProtocolBinary): Any? =
        when (spec.kind) {
            BinaryKind.Uuid -> java.util.UUID(0L, 1L)
            BinaryKind.RestBuffer -> ByteArray(0)
            BinaryKind.Buffer -> ByteArray(if (spec.fixedCount >= 0) spec.fixedCount else 0)
            BinaryKind.AnonOptionalNbt, BinaryKind.OptionalNbt, BinaryKind.CompressedNbt -> null
            BinaryKind.AnonymousNbt, BinaryKind.Nbt ->
                net.lenni0451.mcstructs.nbt.tags.CompoundTag().addString("text", "sample")
        }

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }
}
