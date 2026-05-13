package org.kvxd.kmcprotocol

import io.ktor.utils.io.ByteChannel
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
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.ProtocolDynamicCodec
import org.kvxd.kmcprotocol.core.encoding.ProtocolType
import org.kvxd.kmcprotocol.core.encoding.ProtocolValue
import org.kvxd.kmcprotocol.core.encoding.getElementAnnotationFromIndex
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedPacketSerializationTest {

    @Test
    fun `generated packets can be sample-instantiated and supported packets round trip`() = runBlocking {
        val data = ProtocolData()
        var sampledPackets = 0
        var roundTrippedPackets = 0

        ProtocolState.entries.forEach { state ->
            data.state = state

            state.packets.forEach { packetClass ->
                val metadata = packetClass.findAnnotation<PacketMetadata>()
                assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")

                val serializer = data.registry.getPacketSerializerByClass(state, packetClass)
                assertNotNull(serializer, "${packetClass.qualifiedName} is not registered")

                val packet = samplePacket(serializer)
                sampledPackets += 1

                val channel = ByteChannel()
                data.format.send(packet, channel)
                channel.close()

                val decoded = data.format.receive(channel, metadata.direction)
                assertEquals(packet, decoded, "${packetClass.qualifiedName} failed round-trip encoding")
                roundTrippedPackets += 1
            }
        }

        assertTrue(sampledPackets > 0, "Expected to sample generated packets")
        assertTrue(roundTrippedPackets > 0, "Expected at least one generated packet to round trip")
    }

    @Suppress("UNCHECKED_CAST")
    private fun samplePacket(serializer: KSerializer<out MinecraftPacket>): MinecraftPacket =
        SamplePacketDecoder.decode(serializer)

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

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
        decodeString().also { recordElement(descriptor, index, it) }
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        if (deserializer.descriptor.serialName == "ProtocolValue") {
            @Suppress("UNCHECKED_CAST")
            return ProtocolValue() as T
        }

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
            @Suppress("UNCHECKED_CAST")
            val value = ProtocolDynamicCodec.sample(
                ProtocolDynamicCodec.parse(protocolType.json),
                contextStack.lastOrNull() ?: mutableMapOf(),
            ) as T
            recordElement(descriptor, index, (value as ProtocolValue).value)
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
    ): T? = decodeSerializableValue(deserializer)

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
