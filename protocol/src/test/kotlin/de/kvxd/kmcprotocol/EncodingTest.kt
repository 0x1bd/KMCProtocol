package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import de.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import de.kvxd.kmcprotocol.network.Direction
import de.kvxd.kmcprotocol.packets.handshake.IntentionPacket
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `test encoding and decoding of all types`() {
        val packet = generateTestPacket()

        val data = ProtocolData()

        val channel = ByteChannel()

        val encoder = MinecraftEncoder(data, channel)
        encoder.encodeSerializableValue(TestPacket.serializer(), packet)
        channel.close()

        val decoder = MinecraftDecoder(data, channel)
        val decoded = decoder.decodeSerializableValue(TestPacket.serializer())

        assertEquals(packet, decoded)
    }

    @Test
    fun `test encoding and decoding of intention packet with packet registration`() = runTest {
        val packet = IntentionPacket(769, "localhost", 25565.toUShort(), IntentionPacket.NextState.Status)

        val data = ProtocolData()

        val channel = ByteChannel()

        data.format.send(packet, channel)
        channel.close()

        val decoded = data.format.receive(channel, Direction.Serverbound)

        assertEquals(packet, decoded)
    }

}