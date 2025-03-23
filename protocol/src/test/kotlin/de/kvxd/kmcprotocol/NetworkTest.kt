package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.network.Server
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

    private fun createProtocol() = MinecraftProtocol {
        registerPacket(ServerboundTestPacket::class, ServerboundTestPacket.CODEC)
        registerPacket(ClientboundTestPacket::class, ClientboundTestPacket.CODEC)
    }

    @Test
    fun `client server connection flow`() = runTest {
        val server = Server(protocol = createProtocol())

        var clientConnected = false
        var clientDisconnected = false

        var serverBound = false
        var serverClosed = false

        server.addListener(object : Server.ServerListener() {
            override fun serverBound() {
                serverBound = true
            }

            override fun serverClosing() {
                serverClosed = true
            }

            override fun sessionConnected(session: Server.Session) {
                clientConnected = true
            }

            override fun sessionDisconnected(session: Server.Session) {
                clientDisconnected = true
            }

            override fun error(throwable: Throwable) {
                throwable.printStackTrace()
            }
        })

        server.bind()

        launch {
            val client = Client(protocol = createProtocol())

            client.connect()
            client.disconnect()
        }.join() // Wait for client to finish

        server.close()

        assertTrue(clientConnected)
        assertTrue(clientDisconnected)

        assertTrue(serverBound)
        assertTrue(serverClosed)
    }

    @Test
    fun `client server packet exchange`() = runTest {
        val server = Server(protocol = createProtocol())

        var clientGotPacket = false
        var serverGotPacket = false

        server.addListener(object : Server.ServerListener() {
            override fun sessionConnected(session: Server.Session) {
                session.addListener(object : Server.SessionListener() {
                    override suspend fun packetReceived(packet: MinecraftPacket) {
                        serverGotPacket = true

                        println("Server")

                        session.send(ClientboundTestPacket("Hello, world!"))
                    }
                })
            }
        })

        launch {
            server.bind()
        }

        launch {
            val client = Client(protocol = createProtocol())

            client.addListener(object : Client.ClientListener() {
                override fun packetReceived(packet: MinecraftPacket) {
                    clientGotPacket = true

                    println("Client")

                    server.close()

                    assertTrue { clientGotPacket }
                    assertTrue { serverGotPacket }
                }
            })

            client.connect()

            client.send(ServerboundTestPacket(42))
        }
    }

}