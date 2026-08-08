package org.kvxd.kmcprotocol.network.client

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.KmcSelector
import org.kvxd.kmcprotocol.network.conn.Connection
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware

class Client private constructor(
    protocol: ProtocolData,
    socket: Socket,
    middlewares: List<ProtocolMiddleware>,
) : Connection(
    protocol,
    Direction.Clientbound,
    socket,
    middlewares,
) {

    companion object {

        suspend fun connect(
            address: SocketAddress,
            protocol: ProtocolData,
            selectorManager: SelectorManager = KmcSelector.shared,
        ): Client = connect(address, protocol, selectorManager, emptyList())

        suspend fun connect(
            address: SocketAddress,
            protocol: ProtocolData,
            selectorManager: SelectorManager = KmcSelector.shared,
            middlewares: List<ProtocolMiddleware>,
        ): Client = Client(protocol, aSocket(selectorManager).tcp().connect(address), middlewares)

        suspend fun connect(
            host: String,
            port: Int,
            protocol: ProtocolData,
            selectorManager: SelectorManager = KmcSelector.shared,
        ): Client = connect(InetSocketAddress(host, port), protocol, selectorManager, emptyList())

        suspend fun connect(
            host: String,
            port: Int,
            protocol: ProtocolData,
            selectorManager: SelectorManager = KmcSelector.shared,
            middlewares: List<ProtocolMiddleware>,
        ): Client = connect(InetSocketAddress(host, port), protocol, selectorManager, middlewares)
    }
}
