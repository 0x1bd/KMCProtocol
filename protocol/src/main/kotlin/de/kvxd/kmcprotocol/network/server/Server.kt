package de.kvxd.kmcprotocol.network.server

import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.network.Connection
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import java.util.*

class Server(
    private val port: Int = 25565,
    private val sessionProtocol: () -> ProtocolData
) {

    private lateinit var socket: ServerSocket
    private val selectorManager = SelectorManager()

    private val sessions = Collections.synchronizedSet(mutableSetOf<ServerSession>())

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    suspend fun bind() {
        socket = aSocket(selectorManager)
            .tcp()
            .bind(port = port)

        _events.emit(ServerEvent.ServerBound)

        while (!socket.isClosed) {
            try {
                val sessionSocket = socket.accept()

                val session = ServerSession(sessionSocket, sessionProtocol())
                sessions.add(session)

                _events.emit(ServerEvent.SessionConnected(session))
            } catch (e: Exception) {
                _events.emit(ServerEvent.ServerError(e))
                throw e
            }
        }
    }

    fun close() {
        runBlocking {
            sessions.forEach { it.disconnect() }
            sessions.clear()

            socket.close()
        }
    }

    inner class ServerSession(
        socket: Socket,
        data: ProtocolData
    ) : Connection(data, Direction.Serverbound, socket)

}