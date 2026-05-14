package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.generated.GeneratedProtocol
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `test encoding and decoding of all types`() {
        val packet = generateTestPacket()

        val data = GeneratedProtocol.protocolData()

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
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 1)

        val data = GeneratedProtocol.protocolData()

        val channel = ByteChannel()

        data.send(packet, channel)
        channel.close()

        val decoded = data.receive(channel, Direction.Serverbound)

        assertEquals(packet, decoded)
    }

}
