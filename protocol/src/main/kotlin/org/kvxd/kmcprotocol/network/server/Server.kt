package org.kvxd.kmcprotocol.network.server

import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.KmcProtocolException
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP server that accepts Minecraft packet sessions.
 */
class Server(
    /**
     * Address where the server socket should bind.
     */
    private val bindAddress: SocketAddress = InetSocketAddress("0.0.0.0", 25565),

    /**
     * Factory used to create an independent protocol context for each accepted session.
     */
    private val sessionProtocolData: () -> ProtocolData = { ProtocolData() },
) : AutoCloseable {
    private lateinit var socket: ServerSocket
    private lateinit var selectorManager: SelectorManager

    private val activeSessions = Collections.synchronizedSet(mutableSetOf<ServerSession>())

    private val closed = AtomicBoolean(false)

    private val acceptedSessions = Channel<ServerSession>(Channel.BUFFERED)

    /**
     * Stream of accepted sessions.
     */
    val sessions: ReceiveChannel<ServerSession> = acceptedSessions

    private val serverErrors = Channel<Throwable>(Channel.BUFFERED)

    /**
     * Stream of asynchronous server errors.
     */
    val errors: ReceiveChannel<Throwable> = serverErrors

    private val boundDeferred = CompletableDeferred<Unit>()

    /**
     * Suspends until the server socket has finished binding.
     */
    suspend fun awaitBound() {
        boundDeferred.await()
    }

    /**
     * Address assigned to the bound server socket.
     */
    val localAddress: SocketAddress
        get() = socket.localAddress

    /**
     * Binds the server socket and accepts sessions until the server is closed.
     */
    suspend fun bind() {
        selectorManager = ActorSelectorManager(Dispatchers.IO)
        socket = aSocket(selectorManager)
            .tcp()
            .bind(bindAddress)

        boundDeferred.complete(Unit)

        while (!socket.isClosed) {
            try {
                val sessionSocket = socket.accept()

                val session = ServerSession(sessionSocket, sessionProtocolData())
                activeSessions.add(session)

                acceptedSessions.send(session)
            } catch (e: Exception) {
                if (socket.isClosed) break

                val error = KmcProtocolException(
                    message = """
                    Server accept loop failed.
                    Bind address: $bindAddress
                    Details: ${e.message ?: e::class.simpleName}
                    """.trimIndent(),
                    cause = e,
                )
                serverErrors.trySend(error)
                throw error
            }
        }
    }

    /**
     * Returns the next accepted session or throws the next server error.
     */
    suspend fun accept(): ServerSession =
        select {
            acceptedSessions.onReceive { it }
            serverErrors.onReceive { throw it }
        }

    /**
     * Closes the server socket and all active sessions.
     */
    override fun close() = runBlocking {
        if (!closed.compareAndSet(false, true)) return@runBlocking

        activeSessions.forEach { it.close() }
        activeSessions.clear()

        if (::socket.isInitialized) {
            socket.close()
        }
        if (::selectorManager.isInitialized) {
            selectorManager.close()
        }
        acceptedSessions.close()
        serverErrors.close()
    }
}
