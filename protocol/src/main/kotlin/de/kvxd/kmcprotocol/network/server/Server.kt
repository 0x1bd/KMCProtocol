package de.kvxd.kmcprotocol.network.server

import de.kvxd.kmcprotocol.core.ProtocolData
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*

class Server(
    private val bindAddress: SocketAddress = InetSocketAddress("0.0.0.0", 25565),
    private val sessionProtocolData: () -> ProtocolData = { ProtocolData() },
) {
    private lateinit var socket: ServerSocket

    private val sessions = Collections.synchronizedSet(mutableSetOf<ServerSession>())

    private val callbacks = mutableSetOf<ServerCallback>()

    fun addCallback(callback: ServerCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: ServerCallback) {
        callbacks.remove(callback)
    }

    private val boundDeferred = CompletableDeferred<Unit>()

    suspend fun awaitBound() {
        boundDeferred.await()
    }

    suspend fun bind() {
        socket = aSocket(ActorSelectorManager(Dispatchers.IO))
            .tcp()
            .bind(bindAddress)

        boundDeferred.complete(Unit)
        callbacks.forEach { it.onBound() }

        while (!socket.isClosed) {
            try {
                val sessionSocket = socket.accept()

                val session = ServerSession(sessionSocket, sessionProtocolData())
                sessions.add(session)

                callbacks.forEach { it.onSessionConnected(session) }
            } catch (e: Exception) {
                callbacks.forEach { it.onError(e) }
                throw e
            }
        }
    }

    fun close() = runBlocking {
        callbacks.forEach { it.onClose() }

        sessions.forEach { it.close() }
        sessions.clear()

        socket.close()
    }

}