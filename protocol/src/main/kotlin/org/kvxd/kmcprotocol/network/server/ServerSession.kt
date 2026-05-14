package org.kvxd.kmcprotocol.network.server

import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.conn.Connection
import io.ktor.network.sockets.*

/**
 * Server-side packet connection for one accepted client socket.
 */
class ServerSession(
    socket: Socket, data: ProtocolData
) : Connection(
    data, Direction.Serverbound, socket
)
