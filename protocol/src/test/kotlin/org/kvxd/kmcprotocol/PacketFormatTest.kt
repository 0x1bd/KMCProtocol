package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.generated.GeneratedProtocol
import io.ktor.utils.io.ByteChannel
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PacketFormatTest {

    @Test
    fun `compressed packet frames round trip`() = runTest {
        val sender = GeneratedProtocol.protocolData()
        val receiver = GeneratedProtocol.protocolData()
        sender.enableCompression(1)
        receiver.enableCompression(1)
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 1)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        assertEquals(packet, receiver.receive(channel, Direction.Serverbound))
    }

    @Test
    fun `encrypted packet frames round trip`() = runTest {
        val sender = GeneratedProtocol.protocolData()
        val receiver = GeneratedProtocol.protocolData()
        val sharedSecret = ByteArray(16) { it.toByte() }
        sender.enableEncryption(sharedSecret)
        receiver.enableEncryption(sharedSecret)
        val packet = ServerboundSetProtocolPacket(769, "localhost", 25565, 1)

        val channel = ByteChannel()
        sender.send(packet, channel)
        channel.close()

        assertEquals(packet, receiver.receive(channel, Direction.Serverbound))
    }
}
