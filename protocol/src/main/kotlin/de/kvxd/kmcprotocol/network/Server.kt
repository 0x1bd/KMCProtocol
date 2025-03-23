package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.util.*

class Server(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private lateinit var socket: ServerSocket

    // Thread-safe session collection
    private val sessions: MutableSet<Session> = Collections.synchronizedSet(mutableSetOf<Session>())

    private val listeners = mutableSetOf<ServerListener>()

    suspend fun bind() {
        socket = aSocket(selectorManager).tcp().bind(address)
        listeners.forEach { it.serverBound() }

        val serverLoop: suspend CoroutineScope.() -> Unit = {
            try {
                while (!socket.isClosed) {
                    val sessionSocket = socket.accept()
                    Session(sessionSocket, protocol).also { session ->
                        sessions.add(session)
                        listeners.forEach { it.sessionConnected(session) }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException && !socket.isClosed) { // TODO: Closing logic
                    listeners.forEach { it.error(e) }
                }
            } finally {
                close()
            }
        }

        serverLoop(this.scope)
    }

    fun close() {
        if (!::socket.isInitialized || socket.isClosed || job.isCancelled) {
            return // Already closed or not initialized
        }

        listeners.forEach { it.serverClosing() }

        // Cancel ongoing coroutines
        job.cancel()

        // Close sessions before the socket and selector manager
        runBlocking {
            sessions.toSet().forEach { it.close() }
        }

        socket.close()
        selectorManager.close()
    }

    fun addListener(serverListener: ServerListener) {
        listeners.add(serverListener)
    }

    fun removeListener(serverListener: ServerListener) {
        listeners.remove(serverListener)
    }

    open class ServerListener {
        open fun serverBound() {}
        open fun serverClosing() {}

        open fun sessionConnected(session: Session) {}
        open fun sessionDisconnected(session: Session) {}


        open fun error(throwable: Throwable) {}
    }

    open class SessionListener {
        open fun connected() {}
        open fun disconnected() {}

        open suspend fun packetReceived(packet: MinecraftPacket) {}
        open suspend fun packetError(error: Throwable) {}
    }

    inner class Session(
        private val socket: Socket,
        private val protocol: MinecraftProtocol
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

        private val listeners = mutableSetOf<SessionListener>()

        private val writeChannel = socket.openWriteChannel(autoFlush = true)
        private val readChannel = socket.openReadChannel()

        init {
            listeners.forEach { it.connected() }
            launch { handleConnection() }
        }

        fun addListener(serverListener: SessionListener) {
            listeners.add(serverListener)
        }

        fun removeListener(serverListener: SessionListener) {
            listeners.remove(serverListener)
        }

        private suspend fun handleConnection() {
            try {
                while (isActive) {
                    val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.SERVERBOUND)
                    if (packet != null) {
                        listeners.forEach { it.packetReceived(packet) }
                    }
                }
            } catch (e: Exception) {
                listeners.forEach { it.packetError(e) }
            } finally {
                cleanup()
            }
        }

        suspend fun send(packet: MinecraftPacket) {
            try {
                protocol.packetFormat.send(packet, writeChannel, protocol)
            } catch (e: Exception) {
                listeners.forEach { it.packetError(e) }
                close()
            }
        }

        private fun cleanup() {
            close()
            sessions.remove(this)
            listeners.forEach { it.disconnected() }
            this@Server.listeners.forEach { it.sessionDisconnected(this@Session) }
        }

        fun close() {
            cancel("Session closed")
            socket.close()
        }
    }
}