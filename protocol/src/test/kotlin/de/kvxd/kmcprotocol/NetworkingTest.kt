package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.network.Client
import de.kvxd.kmcprotocol.network.Server
import de.kvxd.kmcprotocol.packets.handshake.serverbound.NextState
import de.kvxd.kmcprotocol.packets.handshake.serverbound.ServerboundHandshakePacket
import de.kvxd.kmcprotocol.packets.handshake.serverbound.toProtocolState
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundPongResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundStatusResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.StatusResponse
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundPingRequestPacket
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundStatusRequestPacket
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NetworkingTest {

    @Test
    fun `vanilla-like status flow`() = runTest {
        val server = Server(protocol = defaultProtocol())

        server.eventBus.handler(Server.Events.ServerBound::class) {
            println("Server bound!")
        }

        server.eventBus.handler(Server.Events.SessionConnected::class) { sessionConnected ->
            println("Session has connected: ${sessionConnected.session.socket.remoteAddress}")

            sessionConnected.session.eventBus.handler(Server.SessionEvents.PacketReceived::class) { packetReceived ->
                println("Session has sent packet: ${packetReceived.packet}")

                val packet = packetReceived.packet

                if (packet is ServerboundHandshakePacket) {
                    val state = packet.nextState.toProtocolState()
                    server.updateProtocolState(state)

                    println("Handshake packet received. Switching state to $state")
                }

                if (packet is ServerboundStatusRequestPacket) {
                    println("Received status request packet. Responding.")

                    sessionConnected.session.send(
                        ClientboundStatusResponsePacket(StatusResponse())
                    )
                }

                if (packet is ServerboundPingRequestPacket) {
                    println("Received ping request packet. Responding")

                    sessionConnected.session.send(
                        ClientboundPongResponsePacket(packet.timestamp)
                    )
                }
            }
        }

        server.eventBus.handler(Server.Events.SessionDisconnected::class) {
            println("Session has disconnected")
        }

        launch {
            server.bind()
        }

        server.awaitBound()


        val client = Client(protocol = defaultProtocol())

        client.eventBus.handler(Client.Events.Connected::class) {
            println("Client has connected")
        }

        client.eventBus.handler(Client.Events.Disconnected::class) {
            println("Client has disconnected")
        }

        client.eventBus.handler(Client.Events.PacketReceivedEvent::class) { event ->
            println("Client received packet: ${event.packet}")
        }

        launch {
            client.connect()
        }

        client.awaitConnected()


        client.send(
            ServerboundHandshakePacket(
                LATEST_PROTOCOL_VERSION,
                "localhost",
                25565,
                NextState.Status
            )
        )

        client.updateProtocolState(ProtocolState.STATUS)

        client.send(
            ServerboundStatusRequestPacket()
        )
    }

}