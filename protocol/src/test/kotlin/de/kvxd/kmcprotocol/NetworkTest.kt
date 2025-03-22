package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import io.ktor.network.sockets.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class NetworkTest {

    @PacketMetadata(
        id = 0x00,
        direction = Direction.SERVERBOUND,
        state = ProtocolState.HANDSHAKE
    )
    data class ServerboundTestPacket(
        val foo: Int,
    ) : MinecraftPacket {
        companion object {
            val CODEC = PacketCodec<ServerboundTestPacket> {
                element(ServerboundTestPacket::foo, VarIntCodec)
            }
        }
    }

    @PacketMetadata(
        id = 0x00,
        direction = Direction.CLIENTBOUND,
        state = ProtocolState.HANDSHAKE
    )
    data class ClientboundTestPacket(
        val foo: String,
    ) : MinecraftPacket {
        companion object {
            val CODEC = PacketCodec<ClientboundTestPacket> {
                element(ClientboundTestPacket::foo, StringCodec)
            }
        }
    }

    @Test
    fun `test client creation`() = runBlocking {
        val protocol = MinecraftProtocol {
            registerPacket(ServerboundTestPacket::class, ServerboundTestPacket.CODEC)
            registerPacket(ClientboundTestPacket::class, ClientboundTestPacket.CODEC)
        }

        val client = Client(InetSocketAddress("localhost", 25565), protocol)

        client.connect()

        client.send(
            ServerboundTestPacket(
                769,
            )
        )

        protocol.direction = Direction.CLIENTBOUND

        client.onPacket { packet ->
            println(packet)
        }

        while (true)
            delay(100)

        client.disconnect()
    }

}