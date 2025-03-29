package de.kvxd.kmcprotocol

import com.kvxd.eventbus.handler
import de.kvxd.kmcprotocol.network.client.CConnected
import de.kvxd.kmcprotocol.network.client.CDisconnected
import de.kvxd.kmcprotocol.network.client.CPacketReceived
import de.kvxd.kmcprotocol.network.client.Client
import de.kvxd.kmcprotocol.network.server.*
import de.kvxd.kmcprotocol.packets.handshake.serverbound.NextState
import de.kvxd.kmcprotocol.packets.handshake.serverbound.ServerboundHandshakePacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import kotlin.test.Test

class NetworkingTest {

    @Test
    fun `test event order`() = runTest {
        val order = mutableListOf<String>()

        val expected = mutableListOf(
            "SrvServerBound",
            "SrvSessionConnected",
            "CConnected",
            "SPacketReceived",
            "CPacketReceived",
            "CDisconnected",
            "SConnectionClosed"
        )

        val server = Server()

        server.handler<SrvServerBound> { order.add("SrvServerBound") }
        server.handler<SrvSessionConnected> {
            order.add("SrvSessionConnected")

            it.session.handler<SPacketReceived> { order.add("SPacketReceived") }
            it.session.handler<SConnectionClosed> { order.add("SConnectionClosed") }
        }

        launch {
            server.bind()
        }
        server.awaitBound()

        val client = Client(InetSocketAddress(hostname = "localhost", port = 25565), defaultProtocol())

        client.handler<CConnected> { order.add("CConnected") }
        client.handler<CDisconnected> { order.add("CDisconnected") }
        client.handler<CPacketReceived> { order.add("CPacketReceived") }

        launch {
            client.connect()
        }
        client.awaitConnected()

        client.send(ServerboundHandshakePacket(0, "", 0, NextState.Status))

        delay(10000)
        println("bob")
    }

}