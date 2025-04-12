package de.kvxd.kmcprotocol.network.server

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.network.Direction
import de.kvxd.kmcprotocol.network.conn.Connection
import io.ktor.network.sockets.*

class ServerSession(
    socket: Socket, data: ProtocolData
) : Connection(
    data, Direction.Serverbound, socket
)