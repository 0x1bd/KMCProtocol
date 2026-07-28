package org.kvxd.kmcprotocol.network.client

import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.conn.Connection
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

class Client private constructor(
    protocol: ProtocolData,
    socket: Socket,
    private val selectorManager: SelectorManager,
) : Connection(
    protocol,
    Direction.Clientbound,
    socket
) {
    constructor(
        address: SocketAddress,
        protocol: ProtocolData = ProtocolData()
    ) : this(protocol, createSocket(address))

    constructor(
        host: String,
        port: Int,
        protocol: ProtocolData = ProtocolData(),
    ) : this(InetSocketAddress(host, port), protocol)

    private constructor(
        protocol: ProtocolData,
        clientSocket: ClientSocket,
    ) : this(protocol, clientSocket.socket, clientSocket.selectorManager)

    override fun closeTransportResources() {
        selectorManager.close()
    }
}

private data class ClientSocket(
    val socket: Socket,
    val selectorManager: SelectorManager,
)

private fun createSocket(address: SocketAddress): ClientSocket = runBlocking {
    val selectorManager = SelectorManager(Dispatchers.IO + SupervisorJob())
    try {
        ClientSocket(aSocket(selectorManager).tcp().connect(address), selectorManager)
    } catch (cause: Throwable) {
        selectorManager.close()
        throw cause
    }
}
