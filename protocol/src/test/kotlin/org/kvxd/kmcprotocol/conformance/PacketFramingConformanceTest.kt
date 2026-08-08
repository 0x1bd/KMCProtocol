package org.kvxd.kmcprotocol.conformance

import org.kvxd.kmcprotocol.generated.Protocols
import io.ktor.utils.io.ByteChannel
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.handshake.serverbound.ServerboundSetProtocolPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PacketFramingConformanceTest {

    @Test
    fun `encrypted kmcp frame stream round trips`() = runTest {
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 2)
        val sharedSecret = ByteArray(16) { index -> (index * 7).toByte() }

        val sender = Protocols.V1_21_4.protocolData()
        val receiver = Protocols.V1_21_4.protocolData()
        sender.enableEncryption(sharedSecret)
        receiver.enableEncryption(sharedSecret)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        assertEquals(packet, receiver.receive(channel, Direction.Serverbound))
    }

    @Test
    fun `compressed and encrypted kmcp frame stream round trips`() = runTest {
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 2)
        val sharedSecret = ByteArray(16) { index -> (index * 11).toByte() }

        val sender = Protocols.V1_21_4.protocolData()
        val receiver = Protocols.V1_21_4.protocolData()
        sender.setCompression(1)
        receiver.setCompression(1)
        sender.enableEncryption(sharedSecret)
        receiver.enableEncryption(sharedSecret)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        assertEquals(packet, receiver.receive(channel, Direction.Serverbound))
    }

    @Test
    fun `raw middleware boundary is unframed uncompressed packet data`() = runTest {
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 2)
        val sharedSecret = ByteArray(16) { index -> (index * 13).toByte() }

        val sender = Protocols.V1_21_4.protocolData()
        val receiver = Protocols.V1_21_4.protocolData()
        sender.setCompression(1)
        receiver.setCompression(1)
        sender.enableEncryption(sharedSecret)
        receiver.enableEncryption(sharedSecret)

        val expectedRawPacket = sender.encodePacketBytes(packet)
        val channel = ByteChannel()
        sender.sendPacketBytes(expectedRawPacket, channel, packet)
        channel.close()

        val frame = receiver.receivePacketBytes(channel, Direction.Serverbound)

        assertContentEquals(expectedRawPacket, frame.bytes)
        assertEquals(packet, receiver.decodePacketBytes(frame.bytes, Direction.Serverbound, frame.frameLength))
    }
}