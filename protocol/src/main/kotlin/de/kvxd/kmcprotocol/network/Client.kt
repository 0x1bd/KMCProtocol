package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.Event
import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.IOException

open class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    val eventBus = EventBus.create()
    private val connectionJob = CompletableDeferred<Unit>()

    class ConnectedEvent : Event
    class DisconnectingEvent : Event
    class ErrorEvent(val cause: Throwable) : Event

    class PacketReceivedEvent(val packet: MinecraftPacket) : Event
    class PacketSendingEvent(val packet: MinecraftPacket) : Event {
        var send: Boolean = true
    }

    class PacketSentEvent(val packet: MinecraftPacket) : Event

    suspend fun connect() {
        try {
            socket = aSocket(selectorManager).tcp().connect(address)
            writeChannel = socket.openWriteChannel(autoFlush = false)
            readChannel = socket.openReadChannel()

            eventBus.post(ConnectedEvent())
            connectionJob.complete(Unit)

            scope.launch {
                try {
                    while (isActive && !readChannel.isClosedForRead) {
                        val packet = try {
                            protocol.packetFormat.receive(readChannel, protocol, Direction.CLIENTBOUND)
                        } catch (e: Exception) {
                            eventBus.post(ErrorEvent(e))
                            break
                        }
                        packet?.let { eventBus.post(PacketReceivedEvent(it)) }
                    }
                } finally {
                    disconnect()
                }
            }
        } catch (e: Exception) {
            connectionJob.completeExceptionally(e)
            disconnect()
            throw e
        }
    }

    suspend fun send(packet: MinecraftPacket) {
        val event = PacketSendingEvent(packet)
        eventBus.post(event)
        if (!event.send) return

        try {
            protocol.packetFormat.send(packet, writeChannel, protocol)
            writeChannel.flush()
            eventBus.post(PacketSentEvent(packet))
        } catch (e: Exception) {
            eventBus.post(ErrorEvent(e))
            disconnect()
            throw e
        }
    }

    fun disconnect() {
        if (!::socket.isInitialized || socket.isClosed) return

        eventBus.post(DisconnectingEvent())
        scope.cancel("Client disconnecting")

        runBlocking {
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
        selectorManager.close()
    }

    suspend fun awaitConnection() = connectionJob.await()
}