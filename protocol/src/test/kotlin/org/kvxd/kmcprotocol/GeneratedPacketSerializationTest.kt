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
import org.kvxd.kmcprotocol.core.encoding.ProtocolDynamicCodec
import org.kvxd.kmcprotocol.core.encoding.ProtocolType
import org.kvxd.kmcprotocol.core.encoding.getElementAnnotationFromIndex
import org.kvxd.kmcprotocol.generated.GeneratedPacketRegistry
import org.kvxd.kmcprotocol.generated.GeneratedProtocol
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketSerializationTest {

    @Test
    fun `generated packets can be sample-instantiated and supported packets round trip`() = runBlocking {
        val data = GeneratedProtocol.protocolData()
        var sampledPackets = 0
        var roundTrippedPackets = 0
        val decodeFailures = mutableListOf<String>()

        ProtocolState.entries.forEach { state ->
            data.switchTo(state)

            GeneratedPacketRegistry.packetsById(state).values.flatMap { it.values }.forEach { packetClass ->
                val metadata = packetClass.findAnnotation<PacketMetadata>()
                assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")

                val serializer = data.registry.getPacketSerializerByClass(state, packetClass)
                assertNotNull(serializer, "${packetClass.qualifiedName} is not registered")

                val packet = samplePacket(serializer)
                sampledPackets += 1

                val channel = ByteChannel()
                data.send(packet, channel)
                channel.close()
                val encoded = channel.readRemaining().readByteArray()

                runCatching {
                    data.receive(ByteReadChannel(encoded), metadata.direction)
                }.onSuccess { decoded ->
                    assertNotNull(decoded, "${packetClass.qualifiedName} failed to decode after encoding")
                    roundTrippedPackets += 1
                }.onFailure { error ->
                    decodeFailures += "${packetClass.qualifiedName}: ${error::class.simpleName}: ${error.message}"
                }
            }
        }

        assertTrue(sampledPackets > 0, "Expected to sample generated packets")
        assertTrue(
            decodeFailures.isEmpty(),
            "Expected every generated packet sample to decode after encoding; failures=${decodeFailures.take(10)}",
        )
        assertEquals(sampledPackets, roundTrippedPackets, "Expected every generated packet sample to round trip")
    }

    @Suppress("UNCHECKED_CAST")
    private fun samplePacket(serializer: KSerializer<out MinecraftPacket>): MinecraftPacket =
        SamplePacketDecoder.decode(serializer)

}

private fun Any?.protocolEquals(other: Any?): Boolean {
    if (this is ByteArray && other is ByteArray) return contentEquals(other)
    if (this is List<*> && other is List<*>) {
        return size == other.size && zip(other).all { (left, right) -> left.protocolEquals(right) }
    }
    if (this != null && other != null && this::class == other::class) {
        if (this::class.java.isEnum) return this == other
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

private object SamplePacketDecoder : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()
    private val indexStack = mutableListOf<Int>()
    private val contextStack = mutableListOf<MutableMap<String, Any?>>()

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
    override fun decodeString(): String = "sample"
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = 0

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        indexStack.add(0)
        contextStack.add(mutableMapOf())
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        indexStack.removeAt(indexStack.lastIndex)
        contextStack.removeAt(contextStack.lastIndex)
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
            val value = ProtocolDynamicCodec.sample(
                ProtocolDynamicCodec.parse(protocolType.json),
                contextStack.lastOrNull() ?: mutableMapOf(),
                GeneratedPacketRegistry.protocolResourcePath,
            ).value?.toString() ?: ""
            recordElement(descriptor, index, value)
            return value
        }

        return decodeString().also { recordElement(descriptor, index, it) }
    }
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        return deserializer.deserialize(this)
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val value = ProtocolDynamicCodec.sampleAny(
                ProtocolDynamicCodec.parse(protocolType.json),
                deserializer,
                contextStack.lastOrNull() ?: mutableMapOf(),
                GeneratedPacketRegistry.protocolResourcePath,
            )
            recordElement(descriptor, index, value)
            return value
        }

        val value = decodeSerializableValue(deserializer)
        recordElement(descriptor, index, value)
        return value
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? {
        val protocolType = descriptor.getElementAnnotationFromIndex<ProtocolType>(index)
        if (protocolType != null) {
            val value = ProtocolDynamicCodec.sampleAny(
                ProtocolDynamicCodec.parse(protocolType.json),
                deserializer,
                contextStack.lastOrNull() ?: mutableMapOf(),
                GeneratedPacketRegistry.protocolResourcePath,
            )
            recordElement(descriptor, index, value)
            return value
        }

        return decodeSerializableValue(deserializer)
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = true

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        throw SerializationException("Generated packet samples do not use null values")
    }

    private fun recordElement(descriptor: SerialDescriptor, index: Int, value: Any?) {
        contextStack.lastOrNull()?.put(descriptor.getElementName(index), value)
    }
}
