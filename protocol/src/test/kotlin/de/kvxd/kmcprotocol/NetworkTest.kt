package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.network.Server
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import kotlinx.coroutines.*
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
        val boundLatch = CompletableDeferred<Unit>()
        val clientConnectedLatch = CompletableDeferred<Unit>()
        val serverClosedLatch = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.BoundEvent::class) { boundLatch.complete(Unit) }
        server.eventBus.handler(Server.SessionConnectedEvent::class) { clientConnectedLatch.complete(Unit) }
        server.eventBus.handler(Server.ClosingEvent::class) { serverClosedLatch.complete(Unit) }

        val serverJob = launch { server.bind() }

        // Wait for server to bind
        boundLatch.await()

        val client = Client(protocol = createProtocol())
        client.connect()
        client.awaitConnection()

        // Verify client connection
        clientConnectedLatch.await()
        client.disconnect()

        server.close()
        serverClosedLatch.await()

        assertTrue(clientConnectedLatch.isCompleted)
        assertTrue(serverClosedLatch.isCompleted)
        serverJob.cancel()
    }


    @Test
    fun `client server packet exchange`() = runTest {
        val server = Server(protocol = createProtocol())
        val testPayload = "Hello, World!"
        val serverReceived = CompletableDeferred<Unit>()
        val clientReceived = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.SessionConnectedEvent::class) { event ->
            event.session.eventBus.handler(Server.Session.PacketReceivedEvent::class) {
                serverReceived.complete(Unit)
                launch {
                    event.session.send(ClientboundTestPacket(testPayload))
                }
            }
        }

        launch { server.bind() }
        server.awaitBound()

        val client = Client(protocol = createProtocol())
        client.eventBus.handler(Client.PacketReceivedEvent::class) {
            if ((it.packet as ClientboundTestPacket).foo == testPayload) {
                clientReceived.complete(Unit)
            }
        }

        client.connect()
        client.awaitConnection()
        client.send(ServerboundTestPacket(42))

        // Wait for both packets to be processed
        withContext(Dispatchers.Default.limitedParallelism(64)) {
            withTimeout(500) {
                clientReceived.await()
                serverReceived.await()
            }
        }

        client.disconnect()
        server.close()

        assertTrue(serverReceived.isCompleted)
        assertTrue(clientReceived.isCompleted)
    }

}