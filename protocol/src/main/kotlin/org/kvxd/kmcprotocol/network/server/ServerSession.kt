package org.kvxd.kmcprotocol.network.server

import io.ktor.network.sockets.Socket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.conn.Connection
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware

class ServerSession internal constructor(
    socket: Socket,
    data: ProtocolData,
    middlewares: List<ProtocolMiddleware> = emptyList(),
) : Connection(
    data,
    Direction.Serverbound,
    socket,
    middlewares,
)
