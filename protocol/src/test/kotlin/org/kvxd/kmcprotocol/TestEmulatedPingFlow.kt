package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.network.conn.ConnectionCallback
import org.kvxd.kmcprotocol.network.server.Server
import org.kvxd.kmcprotocol.network.server.ServerCallback
import org.kvxd.kmcprotocol.network.server.ServerSession
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingStartPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertIs

class TestEmulated {

    @Test
    fun `emulated status flow`() = runBlocking {
        val server = Server(InetSocketAddress("127.0.0.1", 0))
        val receivedStatusRequest = CompletableDeferred<MinecraftPacket>()

        server.addCallback(object : ServerCallback() {

            override fun onSessionConnected(session: ServerSession) {
                println("Session connected: ${session.remoteAddress}")

                session.addCallback(object : ConnectionCallback() {

                    override fun onPacketReceived(packet: MinecraftPacket) {
                        println("Received packet: $packet")

                        when (packet) {
                            is ServerboundSetProtocolPacket -> session.data.state = ProtocolState.Status
                            is ServerboundPingStartPacket -> receivedStatusRequest.complete(packet)
                        }
                    }

                    override fun onDisconnect() {
                        println("Session disconnected.")
                    }
                })
            }

        })

        val serverJob = launch {
            server.bind()
        }

        server.awaitBound()
        val port = (server.localAddress as InetSocketAddress).port

        val client = Client(InetSocketAddress("127.0.0.1", port))

        client.send(ServerboundSetProtocolPacket(769, "localhost", port, 1))

        client.data.state = ProtocolState.Status

        client.send(ServerboundPingStartPacket)

        assertIs<ServerboundPingStartPacket>(withTimeout(5_000) { receivedStatusRequest.await() })

        client.disconnect()
        server.close()
        serverJob.cancelAndJoin()
    }

}