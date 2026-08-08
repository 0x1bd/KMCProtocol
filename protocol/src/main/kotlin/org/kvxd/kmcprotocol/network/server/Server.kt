package org.kvxd.kmcprotocol.network.server

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.network.KmcSelector
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware

class Server private constructor(
    private val socket: ServerSocket,
    middlewares: List<ProtocolMiddleware>,
    private val sessionProtocolData: () -> ProtocolData,
) : AutoCloseable {

    private val middlewares: List<ProtocolMiddleware> = middlewares.toList()
    private val activeSessions: MutableSet<ServerSession> = ConcurrentHashMap.newKeySet()

    private val closed = AtomicBoolean(false)

    val localAddress: SocketAddress
        get() = socket.localAddress

    val isClosed: Boolean
        get() = closed.get() || socket.isClosed

    val openSessions: Int
        get() = activeSessions.size

    val sessions: Flow<ServerSession> = flow {
        while (true) {
            val session = acceptOrNull() ?: break
            emit(session)
        }
    }

    suspend fun accept(): ServerSession =
        acceptOrNull() ?: throw KmcProtocolException("Server socket on $localAddress is closed.")

    suspend fun acceptOrNull(): ServerSession? {
        if (isClosed) return null

        val sessionSocket = try {
            socket.accept()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (isClosed) return null
            throw KmcProtocolException(
                """
                Server failed to accept a connection.
                Bind address: ${runCatching { socket.localAddress }.getOrNull()}
                Details: ${cause.message ?: cause::class.simpleName}
                """.trimIndent(),
                cause,
            )
        }

        val session = ServerSession(sessionSocket, sessionProtocolData(), middlewares)
        activeSessions += session
        session.invokeOnClose { activeSessions -= session }

        if (closed.get()) {
            session.close()
            return null
        }
        return session
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runCatching { socket.close() }
        activeSessions.toList().forEach { session -> runCatching { session.close() } }
        activeSessions.clear()
    }

    companion object {

        suspend fun bind(
            address: SocketAddress = InetSocketAddress("0.0.0.0", 25565),
            selectorManager: SelectorManager = KmcSelector.shared,
            sessionProtocolData: () -> ProtocolData,
        ): Server = bind(address, selectorManager, emptyList(), sessionProtocolData)

        suspend fun bind(
            address: SocketAddress,
            selectorManager: SelectorManager = KmcSelector.shared,
            middlewares: List<ProtocolMiddleware>,
            sessionProtocolData: () -> ProtocolData,
        ): Server = Server(aSocket(selectorManager).tcp().bind(address), middlewares, sessionProtocolData)

        suspend fun bind(
            host: String,
            port: Int,
            selectorManager: SelectorManager = KmcSelector.shared,
            sessionProtocolData: () -> ProtocolData,
        ): Server = bind(InetSocketAddress(host, port), selectorManager, emptyList(), sessionProtocolData)

        suspend fun bind(
            host: String,
            port: Int,
            selectorManager: SelectorManager = KmcSelector.shared,
            middlewares: List<ProtocolMiddleware>,
            sessionProtocolData: () -> ProtocolData,
        ): Server = bind(InetSocketAddress(host, port), selectorManager, middlewares, sessionProtocolData)
    }
}
