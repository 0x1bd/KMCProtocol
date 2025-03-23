package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.Event
import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    private val protocol: MinecraftProtocol
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private lateinit var socket: Socket
    private val connectedDeferred = CompletableDeferred<Unit>()

    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    val eventBus = EventBus.create()

    object Events {
        class Connected : Event
        class Disconnected : Event

        class PacketReceivedEvent(val packet: MinecraftPacket) : Event
    }

    fun updateProtocolState(state: ProtocolState) {
        protocol.state = state
    }

    suspend fun connect() = coroutineScope {
        socket = aSocket(selectorManager).tcp().connect(address)

        writeChannel = socket.openWriteChannel()
        readChannel = socket.openReadChannel()

        connectedDeferred.complete(Unit)

        eventBus.post(Events.Connected())

        while (!socket.isClosed) {
            try {
                val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.CLIENTBOUND)

                packet?.let {
                    eventBus.post(Events.PacketReceivedEvent(it))
                }
            } catch (e: Exception) {
                println("YOU FAILED ME")
                e.printStackTrace()
                disconnect()
            }
        }
    }

    suspend fun awaitConnected() = connectedDeferred.await()

    suspend fun send(packet: MinecraftPacket) {
        println("Send packet: $packet")
        protocol.packetFormat.send(packet, writeChannel, protocol)
    }

    fun disconnect() {
        println("Client closing")

        eventBus.post(Events.Disconnected())

        socket.close()
    }

}