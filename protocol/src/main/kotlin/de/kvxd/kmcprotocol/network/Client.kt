package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    private val listeners = mutableSetOf<ClientListener>()

    suspend fun connect() {
        socket = aSocket(selectorManager)
            .tcp()
            .connect(address)

        writeChannel = socket.openWriteChannel(autoFlush = false)
        readChannel = socket.openReadChannel()

        listeners.forEach { it.connected() }

        scope.launch {
            while (true) {
                val packet = protocol.packetFormat.receive(readChannel, protocol, Direction.CLIENTBOUND)

                packet?.let { packet ->
                    listeners.forEach { listener -> listener.packetReceived(packet) }
                }
            }
        }
    }

    open class ClientListener {
        open fun connected() {}
        open fun disconnecting() {}

        open fun packetReceived(packet: MinecraftPacket) {}
        open fun packetSending(packet: MinecraftPacket): Boolean = true
        open fun packetSent(packet: MinecraftPacket) {}
    }

    fun addListener(clientListener: ClientListener) {
        listeners.add(clientListener)
    }

    fun removeListener(clientListener: ClientListener) {
        listeners.remove(clientListener)
    }

    suspend fun send(packet: MinecraftPacket) {
        if (listeners.any { !it.packetSending(packet) }) return
        protocol.packetFormat.send(packet, writeChannel, protocol)
        listeners.forEach { it.packetSent(packet) }
    }

    fun disconnect() {
        listeners.forEach { it.disconnecting() }

        scope.cancel("Client disconnecting")

        runBlocking {
            socket.close()
        }

        selectorManager.close()
    }

}