package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.network.Server
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import io.ktor.network.sockets.*
import kotlinx.coroutines.launch
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
    fun `test full networking`() {
        runBlocking {

            launch {
                val protocol = MinecraftProtocol {
                    registerPacket(ServerboundTestPacket::class, ServerboundTestPacket.CODEC)
                    registerPacket(ClientboundTestPacket::class, ClientboundTestPacket.CODEC)
                }

                val server = Server(protocol = protocol)

                server.addListener(object : Server.ServerListener() {

                    override fun sessionConnected(session: Server.Session) {
                        session.addListener(object : Server.SessionListener() {
                            override suspend fun packetReceived(packet: MinecraftPacket) {
                                println("Client to server: $packet")

                                session.send(ClientboundTestPacket("Hello, World!"))
                            }
                        })
                    }
                })

                server.bind()
            }

            launch {
                val protocol = MinecraftProtocol {
                    registerPacket(ServerboundTestPacket::class, ServerboundTestPacket.CODEC)
                    registerPacket(ClientboundTestPacket::class, ClientboundTestPacket.CODEC)
                }

                val client = Client(InetSocketAddress("localhost", 25565), protocol)

                client.addListener(object : Client.ClientListener() {

                    override fun packetReceived(packet: MinecraftPacket) {
                        println("Client received $packet")
                    }

                    override fun packetSent(packet: MinecraftPacket) {
                        println("Client sent: $packet")
                    }

                })

                client.connect()

                client.send(
                    ServerboundTestPacket(
                        769,
                    )
                )
            }
        }
    }

}