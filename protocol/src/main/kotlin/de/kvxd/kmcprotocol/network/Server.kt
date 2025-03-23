package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.Event
import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.util.*

class Server(
    private val address: SocketAddress = InetSocketAddress("0.0.0.0", 25565),
    private val protocol: MinecraftProtocol
) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private lateinit var socket: ServerSocket
    private val boundDeferred = CompletableDeferred<Unit>()

    val sessions = Collections.synchronizedSet(mutableSetOf<Session>())

    val eventBus = EventBus.create()

    object Events {
        class ServerBound : Event
        class ServerClosing : Event
        class ServerClosed : Event

        class SessionConnected(val session: Session) : Event
        class SessionDisconnected(val session: Session) : Event
    }

    fun updateProtocolState(state: ProtocolState) {
        protocol.state = state
    }

    suspend fun bind() {
        socket = aSocket(selectorManager).tcp().bind(address)
        eventBus.post(Events.ServerBound())
        boundDeferred.complete(Unit)

        while (!socket.isClosed) {
            val sessionSocket = socket.accept()

            Session(sessionSocket).also { session ->
                sessions.add(session)
                eventBus.post(Events.SessionConnected(session))
            }
        }
    }

    suspend fun awaitBound() = boundDeferred.await()

    fun close() {
        eventBus.post(Events.ServerClosing())

        socket.close()
        selectorManager.close()

        eventBus.post(Events.ServerClosed())
    }

    object SessionEvents {
        class PacketReceived(val packet: MinecraftPacket) : Event
    }

    inner class Session(
        val socket: Socket
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

        private val writeChannel = socket.openWriteChannel()
        private val readChannel = socket.openReadChannel()

        val eventBus = EventBus.create()

        init {
            launch {
                try {
                    do {
                        val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.SERVERBOUND)

                        packet?.let { eventBus.post(SessionEvents.PacketReceived(packet)) }
                    } while (!socket.isClosed && isActive)
                } catch (e: Exception) {
                    close()
                }
            }
        }

        fun send(packet: MinecraftPacket) = launch {
            protocol.packetFormat.send(packet, writeChannel, protocol)
        }

        fun close() {
            sessions.remove(this)
            this@Server.eventBus.post(Events.SessionDisconnected(this))

            socket.close()
        }
    }

}