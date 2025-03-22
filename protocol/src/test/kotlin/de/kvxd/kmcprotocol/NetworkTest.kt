package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.UShortCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import io.ktor.network.sockets.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class NetworkTest {

    @PacketMetadata(
        id = 0x00,
        direction = Direction.SERVERBOUND,
        state = ProtocolState.HANDSHAKE
    )
    data class HandshakePacket(
        val protocolVersion: Int,
        val hostname: String,
        val port: Int,
        val intent: Int
    ) : MinecraftPacket {
        companion object {
            val CODEC = PacketCodec<HandshakePacket> {
                element(HandshakePacket::protocolVersion, VarIntCodec)
                element(HandshakePacket::hostname, StringCodec)
                element(HandshakePacket::port, UShortCodec)
                element(HandshakePacket::intent, VarIntCodec)
            }
        }
    }

    @Test
    fun `test client creation`() = runBlocking {
        val protocol = MinecraftProtocol()

        val registry = PacketRegistry.create(protocol) {
            registerPacket(HandshakePacket::class, HandshakePacket.CODEC)
        }

        val client = Client(InetSocketAddress("localhost", 25565), protocol, registry)

        client.connect()

        client.send(
            HandshakePacket(
                769,
                "localhost",
                25565,
                2
            )
        )

        client.disconnect()
    }

}