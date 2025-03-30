package de.kvxd.kmcprotocol.network.server

import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.network.Connection
import de.kvxd.kmcprotocol.packet.Direction
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.runBlocking
import java.util.*

class Server(
    private val port: Int = 25565,
    private val sessionProtocol: () -> MinecraftProtocol
) {

    private lateinit var socket: ServerSocket
    private val selectorManager = SelectorManager()

    private val sessions = Collections.synchronizedSet(mutableSetOf<ServerSession>())

    val bus = EventBus()

    init {
        bus.onError = { cause -> cause.printStackTrace() }
    }

    suspend fun bind() {
        socket = aSocket(selectorManager)
            .tcp()
            .bind(port = port)

        bus.post(ServerBound)

        while (!socket.isClosed) {
            try {
                val sessionSocket = socket.accept()

                val session = ServerSession(sessionSocket, sessionProtocol())
                sessions.add(session)

                bus.post(SessionConnected(session))
            } catch (e: Exception) {
                bus.post(ServerError(e))
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
        protocol: MinecraftProtocol
    ) : Connection(protocol, Direction.SERVERBOUND, socket)

}