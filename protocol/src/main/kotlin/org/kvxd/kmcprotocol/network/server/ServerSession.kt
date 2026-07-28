package org.kvxd.kmcprotocol.network.server

import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.conn.Connection
import io.ktor.network.sockets.*

class ServerSession(
    socket: Socket, data: ProtocolData
) : Connection(
    data, Direction.Serverbound, socket
)
