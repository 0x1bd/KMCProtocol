package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.handshake.serverbound.ServerboundSetProtocolPacket
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `test encoding and decoding of all types`() {
        val packet = generateTestPacket()

        val writer = PacketWriter()
        MinecraftEncoder(writer).encodeSerializableValue(TestPacket.serializer(), packet)

        val decoded = MinecraftDecoder(PacketReader(writer.toByteArray()))
            .decodeSerializableValue(TestPacket.serializer())

        assertEquals(packet, decoded)
    }

    @Test
    fun `test encoding and decoding of intention packet with packet registration`() = runTest {
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 1)

        val data = Protocols.V1_21_4.protocolData()

        val channel = ByteChannel()

        data.send(packet, channel)
        channel.close()

        val decoded = data.receive(channel, Direction.Serverbound)

        assertEquals(packet, decoded)
    }

}
