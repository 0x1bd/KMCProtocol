package de.kvxd.kmcprotocol.network.client

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.network.Connection
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

class Client(
    address: SocketAddress,
    data: ProtocolData
) : Connection(
    data,
    Direction.Clientbound,
    createSocket(address)
)

private fun createSocket(address: SocketAddress): Socket = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.IO + SupervisorJob())
    aSocket(selectorManager).tcp().connect(address)
}