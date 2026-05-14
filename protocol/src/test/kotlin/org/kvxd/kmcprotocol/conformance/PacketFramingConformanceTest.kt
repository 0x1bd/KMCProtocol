package org.kvxd.kmcprotocol.conformance

import io.ktor.utils.io.ByteChannel
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PacketFramingConformanceTest {

    @Test
    fun `encrypted kmcp frame stream round trips`() = runTest {
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 2)
        val sharedSecret = ByteArray(16) { index -> (index * 7).toByte() }

        val sender = ProtocolData()
        val receiver = ProtocolData()
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

        val sender = ProtocolData()
        val receiver = ProtocolData()
        sender.enableCompression(1)
        receiver.enableCompression(1)
        sender.enableEncryption(sharedSecret)
        receiver.enableEncryption(sharedSecret)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        assertEquals(packet, receiver.receive(channel, Direction.Serverbound))
    }
}
