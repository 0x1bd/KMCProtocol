package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.network.ConnectionEvent
import de.kvxd.kmcprotocol.network.server.Server
import de.kvxd.kmcprotocol.network.server.ServerEvent
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NetworkingTest {

    @Test
    fun `test client and server ping flow`() = runTest {
        val server = Server { ProtocolData() }

        launch {
            server.events
                .filterIsInstance<ServerEvent.SessionConnected>()
                .collect { e ->
                    println("Session connected: ${e.session.remoteAddress}")

                    launch {
                        e.session.events.filterIsInstance<ConnectionEvent.PacketReceived>().collect { p ->
                            println(p.packet)
                        }
                    }
                }
        }

        server.bind()
    }

}