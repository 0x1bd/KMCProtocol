package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.network.server.Server
import org.kvxd.kmcprotocol.packets.generated.configuration.clientbound.ClientboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.configuration.serverbound.ServerboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.login.clientbound.ClientboundCompressPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginAcknowledgedPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginStartPacket
import org.kvxd.kmcprotocol.packets.generated.status.serverbound.ServerboundPingStartPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class TestEmulated {

    @Test
    fun `emulated status flow`() = runBlocking {
        val server = Server(InetSocketAddress("127.0.0.1", 0))

        val serverJob = launch {
            server.bind()
        }

        server.awaitBound()
        val port = (server.localAddress as InetSocketAddress).port

        val client = Client(InetSocketAddress("127.0.0.1", port))
        val session = withTimeout(5_000.milliseconds) { server.accept() }

        client.send(ServerboundSetProtocolPacket(769, "localhost", port, 1))
        assertEquals(ProtocolState.Status, client.data.state)
        assertIs<ServerboundSetProtocolPacket>(withTimeout(5_000.milliseconds) { session.receive() })
        assertEquals(ProtocolState.Status, session.data.state)

        client.send(ServerboundPingStartPacket)

        assertIs<ServerboundPingStartPacket>(withTimeout(5_000.milliseconds) { session.receive() })

        client.close()
        server.close()
        serverJob.cancelAndJoin()
    }

    @Test
    fun `emulated login configuration and play flow with compression`() = runBlocking {
        val server = Server(InetSocketAddress("127.0.0.1", 0))

        val serverJob = launch {
            server.bind()
        }

        server.awaitBound()
        val port = (server.localAddress as InetSocketAddress).port
        val client = Client(InetSocketAddress("127.0.0.1", port))

        val serverSessionJob = launch {
            val session = withTimeout(5_000.milliseconds) { server.accept() }
            assertIs<ServerboundSetProtocolPacket>(withTimeout(5_000.milliseconds) { session.receive() })
            assertEquals(ProtocolState.Login, session.data.state)
            assertIs<ServerboundLoginStartPacket>(withTimeout(5_000.milliseconds) { session.receive() })
            session.send(ClientboundCompressPacket(threshold = 1))
            assertIs<ServerboundLoginAcknowledgedPacket>(withTimeout(5_000.milliseconds) { session.receive() })
            assertEquals(ProtocolState.Configuration, session.data.state)
            session.send(ClientboundFinishConfigurationPacket)
            assertIs<ServerboundFinishConfigurationPacket>(withTimeout(5_000.milliseconds) { session.receive() })
            assertEquals(ProtocolState.Play, session.data.state)
        }

        client.send(ServerboundSetProtocolPacket(769, "localhost", port, 2))
        assertEquals(ProtocolState.Login, client.data.state)

        client.send(
            ServerboundLoginStartPacket(
                username = "KmcProtocol",
                playerUUID = "00000000-0000-0000-0000-000000000001",
            )
        )

        assertIs<ClientboundCompressPacket>(withTimeout(5_000.milliseconds) { client.receive() })
        assertEquals(ProtocolState.Login, client.data.state)
        client.send(ServerboundLoginAcknowledgedPacket)
        assertEquals(ProtocolState.Configuration, client.data.state)

        assertIs<ClientboundFinishConfigurationPacket>(withTimeout(5_000.milliseconds) { client.receive() })
        client.send(ServerboundFinishConfigurationPacket)
        assertEquals(ProtocolState.Play, client.data.state)

        serverSessionJob.join()

        client.close()
        server.close()
        serverJob.cancelAndJoin()
    }

}