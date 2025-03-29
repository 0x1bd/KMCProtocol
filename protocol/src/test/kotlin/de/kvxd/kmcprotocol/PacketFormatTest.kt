package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.PacketFormat
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketFormatTest {

    @Test
    fun `test if packet format is correctly sending and receiving packets`() = runTest {
        val packet = TestPacket.generateTestPacket()

        val protocol = MinecraftProtocol {
            registerPacket(TestPacket::class, TestPacket.CODEC)
        }

        val channel = ByteChannel()

        PacketFormat.Uncompressed.send(packet, channel, protocol)

        val received = PacketFormat.Uncompressed.receive(channel, protocol, Direction.SERVERBOUND)

        assertEquals(packet, received)
    }


}