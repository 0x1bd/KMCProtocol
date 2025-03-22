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
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private lateinit var socket: ServerSocket

    // Thread-safe session collection
    private val sessions: MutableSet<Session> = Collections.synchronizedSet(mutableSetOf<Session>())

    private val listeners = mutableSetOf<ServerListener>()

    suspend fun bind() {
        socket = aSocket(selectorManager).tcp().bind(address)

        listeners.forEach { it.serverBound() }

        try {
            while (isActive) {
                val sessionSocket = socket.accept()
                Session(sessionSocket, protocol).also { session ->
                    sessions.add(session)
                    listeners.forEach { it.sessionConnected(session) }
                }
            }
        } catch (e: CancellationException) {
            println("Server binding cancelled")
        }
    }

    fun close() {
        listeners.forEach { it.serverClosing() }

        cancel("Server shutdown")
        socket.close()
        selectorManager.close()
        sessions.toSet().forEach { it.close() }
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
    }

    open class SessionListener {
        open fun connected() {}
        open fun disconnected() {}

        open suspend fun packetReceived(packet: MinecraftPacket) {}
    }

    inner class Session(
        private val socket: Socket,
        private val protocol: MinecraftProtocol
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

        private val listeners = mutableSetOf<SessionListener>()

        private val writeChannel = socket.openWriteChannel()
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
                coroutineScope {
                    launch { readPackets() }
                }
            } finally {
                close()
                sessions.remove(this)
                listeners.forEach { it.disconnected() }
                this@Server.listeners.forEach { it.sessionDisconnected(this) }
            }
        }

        private suspend fun readPackets() {
            try {
                while (isActive) {
                    protocol.packetFormat.receive(readChannel, protocol, Direction.SERVERBOUND)?.let { packet ->
                        listeners.forEach { it.packetReceived(packet) }
                    }
                }
            } catch (e: Exception) {
                println("Error reading packets: ${e.message}")
            }
        }

        suspend fun send(packet: MinecraftPacket) {
            try {
                protocol.packetFormat.send(packet, writeChannel, protocol)
            } catch (e: Exception) {
                println("Error sending packet: ${e.message}")
                close()
            }
        }

        fun close() {
            cancel("Session closed")
            socket.close()
            listeners.forEach { it.disconnected() }
        }
    }
}