package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.ProtocolState
import de.kvxd.kmcprotocol.network.client.Client
import de.kvxd.kmcprotocol.network.conn.ConnectionCallback
import de.kvxd.kmcprotocol.network.server.Server
import de.kvxd.kmcprotocol.network.server.ServerCallback
import de.kvxd.kmcprotocol.network.server.ServerSession
import de.kvxd.kmcprotocol.packets.handshake.IntentionPacket
import de.kvxd.kmcprotocol.packets.status.ServerboundStatusRequestPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TestEmulated {

    @Test
    fun `emulated status flow`() = runTest {
        val server = Server()

        server.addCallback(object : ServerCallback() {

            override fun onSessionConnected(session: ServerSession) {
                println("Session connected: ${session.remoteAddress}")

                session.addCallback(object : ConnectionCallback() {

                    override fun onPacketReceived(packet: MinecraftPacket) {
                        println("Received packet: $packet")

                        if (packet is IntentionPacket)
                            session.data.state = packet.nextState.toProtocolState()
                    }

                    override fun onDisconnect() {
                        println("Session disconnected.")
                    }
                })
            }

        })

        launch {
            server.bind()
        }

        server.awaitBound()

        val client = Client(InetSocketAddress("0.0.0.0", 25565))

        client.send(IntentionPacket(760, "localhost", 25565.toUShort(), IntentionPacket.NextState.Status))

        client.data.state = ProtocolState.Status

        client.send(ServerboundStatusRequestPacket)
    }

}
