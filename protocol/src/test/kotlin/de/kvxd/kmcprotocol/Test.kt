package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.network.Server
import de.kvxd.kmcprotocol.packets.handshake.serverbound.ServerboundHandshakePacket
import de.kvxd.kmcprotocol.packets.handshake.serverbound.toProtocolState
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundPongResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundStatusResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.StatusPlayers
import de.kvxd.kmcprotocol.packets.status.clientbound.StatusResponse
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundPingRequestPacket
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundStatusRequestPacket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class Test {

    @Test
    fun server() = runTest {
        val protocol = defaultProtocol()
        val server = Server(protocol = protocol)

        server.eventBus.handler(Server.SessionConnectedEvent::class) { event ->
            println("Session connected: ${event.session}")

            event.session.eventBus.handler(Server.Session.PacketReceivedEvent::class) { sessionEvent ->
                println("SERVER GOT PACKET: ${sessionEvent.packet}")

                val packet = sessionEvent.packet

                if (packet is ServerboundHandshakePacket) {
                    println("Got handshake, switching to ${packet.nextState.toProtocolState()}")
                    server.protocol.state = packet.nextState.toProtocolState()
                    //event.session.protocol.state = packet.nextState.toProtocolState()
                }

                if (packet is ServerboundStatusRequestPacket) {
                    runBlocking {
                        println("responding to status request...")
                        event.session.send(
                            ClientboundStatusResponsePacket(
                                StatusResponse(
                                    players = StatusPlayers(69, 0, listOf())
                                )
                            )
                        )
                    }
                }

                if (packet is ServerboundPingRequestPacket) {
                    runBlocking {
                        println("responding to ping request... (${packet.timestamp})")
                        event.session.send(ClientboundPongResponsePacket(packet.timestamp))
                    }
                }
            }
        }

        server.eventBus.handler(Server.SessionDisconnectedEvent::class) {
            println("Disconnected: ${it.session}")
        }

        server.bind(true)
        server.awaitBound()
    }
    /*
    @Test
    fun a() = runTest {
        server()

        val protocol = defaultProtocol()

        val client = Client(protocol = protocol)

        client.eventBus.handler(Client.PacketReceivedEvent::class) { event ->
            println("Received: ${event.packet}")
        }

        client.eventBus.handler(Client.PacketSentEvent::class) { event ->
            println("sent: ${event.packet}")
        }

        client.connect()
        client.awaitConnection()

        protocol.state = ProtocolState.HANDSHAKE

        client.send(
            ServerboundHandshakePacket(
                LATEST_PROTOCOL_VERSION,
                "localhost",
                25565,
                NextState.Status
            )
        )

        protocol.state = ProtocolState.STATUS

        client.send(
            ServerboundStatusRequestPacket()
        )

        launch {
            while (true)
                delay(5)
        }
    }
     */

}