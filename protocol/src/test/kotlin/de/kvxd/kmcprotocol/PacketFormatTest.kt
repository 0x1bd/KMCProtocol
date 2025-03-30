package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.format.Compressed
import de.kvxd.kmcprotocol.packet.format.Uncompressed
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketFormatTest {

    @Test
    fun `test uncompressed format`() = runTest {
        val packet = TestPacket.generateTestPacket()

        val protocol = MinecraftProtocol {
            registerPacket(TestPacket::class, TestPacket.CODEC)
        }

        val channel = ByteChannel()

        Uncompressed.send(packet, channel, protocol)

        val received = Uncompressed.receive(channel, protocol, Direction.SERVERBOUND)

        assertEquals(packet, received)
    }

    @Test
    fun `test compressed format`() = runTest {
        val packet = TestPacket.generateTestPacket()
        val format = Compressed()

        val protocol = MinecraftProtocol {
            registerPacket(TestPacket::class, TestPacket.CODEC)
        }

        val channel = ByteChannel()

        format.send(packet, channel, protocol)

        val received = format.receive(channel, protocol, Direction.SERVERBOUND)

        assertEquals(packet, received)
    }

    @Test
    fun `test compressed format with large packet`() = runTest {
        val packet = TestPacket.generateLargeTestPacket()
        val format = Compressed()

        val protocol = MinecraftProtocol {
            registerPacket(TestPacket::class, TestPacket.CODEC)
        }

        val channel = ByteChannel()

        format.send(packet, channel, protocol)

        val received = format.receive(channel, protocol, Direction.SERVERBOUND)

        assertEquals(packet, received)
    }

}