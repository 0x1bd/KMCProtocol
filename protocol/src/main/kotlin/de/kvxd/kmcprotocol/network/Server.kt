package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.Event
import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class Server(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private lateinit var socket: ServerSocket

    val sessions = Collections.synchronizedSet(mutableSetOf<Session>())
    private val boundDeferred = CompletableDeferred<Unit>()

    val eventBus = EventBus.create()

    class BoundEvent : Event
    class ClosingEvent : Event
    class ErrorEvent(val throwable: Throwable) : Event
    class SessionConnectedEvent(val session: Session) : Event
    class SessionDisconnectedEvent(val session: Session) : Event

    suspend fun bind(block: Boolean = false) {
        val s = CoroutineScope(Dispatchers.IO)
        if (block)
            _bind()
        else
            s.launch {
                _bind()
            }
    }

    private suspend fun _bind() {
        try {
            socket = aSocket(selectorManager).tcp().bind(address)
            eventBus.post(BoundEvent())
            boundDeferred.complete(Unit)

            while (isActive()) {
                val sessionSocket = try {
                    socket.accept()
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    eventBus.post(ErrorEvent(e))
                    break
                }

                Session(sessionSocket, protocol).also { session ->
                    sessions.add(session)
                    eventBus.post(SessionConnectedEvent(session))
                }
            }
        } finally {
            close()
        }
    }

    suspend fun awaitBound() = boundDeferred.await()

    fun close() {
        if (!::socket.isInitialized || socket.isClosed) return

        eventBus.post(ClosingEvent())

        runBlocking {
            sessions.toSet().forEach { it.close() }
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
        selectorManager.close()
    }

    private fun isActive() = !socket.isClosed

    inner class Session(
        private val socket: Socket,
        private val protocol: MinecraftProtocol
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

        private val writeChannel = socket.openWriteChannel(autoFlush = true)
        private val readChannel = socket.openReadChannel()

        val eventBus = EventBus.create()
        private val sessionJob = CompletableDeferred<Unit>()

        inner class ConnectedEvent : Event
        inner class DisconnectedEvent : Event
        inner class PacketReceivedEvent(val packet: MinecraftPacket) : Event
        inner class ErrorEvent(val throwable: Throwable) : Event

        init {
            eventBus.post(ConnectedEvent())
            launch {
                try {
                    handleConnection()
                } finally {
                    cleanup()
                }
            }
        }

        private suspend fun handleConnection() {
            try {
                while (isActive && !readChannel.isClosedForRead) {
                    val packet = try {
                        protocol.packetFormat.receive(readChannel, protocol, Direction.SERVERBOUND)
                    } catch (e: Exception) {
                        eventBus.post(ErrorEvent(e))
                        break
                    }
                    packet?.let { eventBus.post(PacketReceivedEvent(it)) }
                }
            } finally {
                sessionJob.complete(Unit)
            }
        }

        suspend fun send(packet: MinecraftPacket) {
            try {
                protocol.packetFormat.send(packet, writeChannel, protocol)
            } catch (e: Exception) {
                eventBus.post(ErrorEvent(e))
                close()
                throw e
            }
        }

        private fun cleanup() {
            sessions.remove(this)
            eventBus.post(DisconnectedEvent())
            this@Server.eventBus.post(SessionDisconnectedEvent(this))
            close()
        }

        fun close() {
            coroutineContext.cancel()
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }

        suspend fun awaitTermination() = sessionJob.await()
    }
}