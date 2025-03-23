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
import kotlin.test.assertEquals
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

        val boundDeferred = CompletableDeferred<Unit>()
        val clientConnectedDeferred = CompletableDeferred<Unit>()
        val serverClosingDeferred = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.BoundEvent::class) { boundDeferred.complete(Unit) }
        server.eventBus.handler(Server.SessionConnectedEvent::class) { clientConnectedDeferred.complete(Unit) }
        server.eventBus.handler(Server.ClosingEvent::class) { serverClosingDeferred.complete(Unit) }

        server.bind()
        boundDeferred.await()

        val client = Client(protocol = createProtocol())
        client.connect()
        client.awaitConnection()

        clientConnectedDeferred.await()
        client.disconnect()

        server.close()
        serverClosingDeferred.await()

        assertTrue(clientConnectedDeferred.isCompleted)
        assertTrue(serverClosingDeferred.isCompleted)
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

        server.bind()
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

    @Test
    fun `multiple clients simultaneously`() = runTest {
        val server = Server(protocol = createProtocol())

        val clients = 5

        val connected = CompletableDeferred<Unit>()
        val packetsReceived = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.SessionConnectedEvent::class) {
            connected.complete(Unit)

            it.session.eventBus.handler(Server.Session.PacketReceivedEvent::class) {
                packetsReceived.complete(Unit)
            }
        }

        server.bind()

        server.awaitBound()

        for (i in 0 until clients) {
            val client = Client(protocol = createProtocol())

            client.connect()

            client.send(ServerboundTestPacket(42))

            client.disconnect()
        }

        server.close()

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                connected.await()
                packetsReceived.await()
            }
        }
    }

    @Test
    fun `handle server-initiated disconnects`() = runTest {
        val server = Server(protocol = createProtocol())

        val disconnectDeferred = CompletableDeferred<Unit>()
        val clientDisconnectDeferred = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.SessionConnectedEvent::class) { event ->
            launch {
                delay(100)
                event.session.close()
            }
        }

        server.eventBus.handler(Server.SessionDisconnectedEvent::class) {
            disconnectDeferred.complete(Unit)
        }

        server.bind()
        server.awaitBound()

        val client = Client(protocol = createProtocol())

        client.eventBus.handler(Client.DisconnectingEvent::class) { event ->
            clientDisconnectDeferred.complete(Unit)
        }

        client.connect()
        client.awaitConnection()

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                disconnectDeferred.await()
                clientDisconnectDeferred.await()
            }
        }

        server.close()

        assertTrue { disconnectDeferred.isCompleted }
        assertTrue { clientDisconnectDeferred.isCompleted }
    }

    @Test
    fun `proper event ordering during connection lifecycle`() = runTest {
        val server = Server(protocol = createProtocol())

        val eventOrder = mutableListOf<String>()
        val expectedOrder = listOf(
            "ServerBound",
            // "SessionConnected", ----- The order of SessionConnected and ClientConnected can change dynamically. We can't control it -----
            // "ClientConnected",
            "PacketSending",
            "PacketSent",
            "PacketReceived",
            "ClientDisconnecting",
            "ServerClosing",
            "SessionDisconnected"
        )

        val serverBound = CompletableDeferred<Unit>()
        val sessionConnected = CompletableDeferred<Unit>()
        val clientConnected = CompletableDeferred<Unit>()
        val packetSent = CompletableDeferred<Unit>()
        val packetReceived = CompletableDeferred<Unit>()
        val sessionDisconnected = CompletableDeferred<Unit>()

        server.eventBus.handler(Server.BoundEvent::class) {
            eventOrder.add("ServerBound")
            serverBound.complete(Unit)
        }
        server.eventBus.handler(Server.SessionConnectedEvent::class) { event ->
            sessionConnected.complete(Unit)
            event.session.eventBus.handler(Server.Session.PacketReceivedEvent::class) {
                println("PACKET")
                eventOrder.add("PacketReceived")
                packetReceived.complete(Unit)
            }
        }
        server.eventBus.handler(Server.SessionDisconnectedEvent::class) {
            eventOrder.add("SessionDisconnected")
            sessionDisconnected.complete(Unit)
        }
        server.eventBus.handler(Server.ClosingEvent::class) { eventOrder.add("ServerClosing") }

        server.bind()
        server.awaitBound()

        val client = Client(protocol = createProtocol())

        client.eventBus.handler(Client.ConnectedEvent::class) {
            clientConnected.complete(Unit)
        }
        client.eventBus.handler(Client.PacketSendingEvent::class) { eventOrder.add("PacketSending") }
        client.eventBus.handler(Client.PacketSentEvent::class) {
            eventOrder.add("PacketSent")
            packetSent.complete(Unit)
        }
        client.eventBus.handler(Client.DisconnectingEvent::class) { eventOrder.add("ClientDisconnecting") }

        client.connect()

        // Wait for the server to be bound and the session to be connected before proceeding
        serverBound.await()
        sessionConnected.await()
        client.awaitConnection()
        clientConnected.await()

        // Send the packet and wait for it to be sent and received
        client.send(ServerboundTestPacket(42))
        packetSent.await()
        packetReceived.await()

        client.disconnect()
        server.close()

        // Wait for the session to be disconnected before proceeding
        sessionDisconnected.await()

        server.sessions.forEach { session ->
            runBlocking { session.awaitTermination() }
        }

        assertEquals(expectedOrder, eventOrder)
    }

}