package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import de.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import io.ktor.utils.io.*
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `test encoding and decoding of all types`() {
        val packet = generateTestPacket()

        val data = ProtocolData()

        val channel = ByteChannel()
        val sink = channel.asSink().buffered()
        val source = channel.asSource().buffered()

        val encoder = MinecraftEncoder(data, sink)
        encoder.encodeSerializableValue(TestPacket.serializer(), packet)
        sink.close()

        val decoder = MinecraftDecoder(data, source)
        val decoded = decoder.decodeSerializableValue(TestPacket.serializer())
        source.close()

        assertEquals(packet, decoded)
    }

}