package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketHeader
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class Client(
    private val address: SocketAddress = InetSocketAddress("localhost", 25565),
    val protocol: MinecraftProtocol
) {

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var socket: Socket
    private lateinit var writeChannel: ByteWriteChannel
    private lateinit var readChannel: ByteReadChannel

    private val packetFlow = MutableSharedFlow<MinecraftPacket>()

    suspend fun connect() {
        socket = aSocket(selectorManager)
            .tcp()
            .connect(address)

        writeChannel = socket.openWriteChannel(autoFlush = false)
        readChannel = socket.openReadChannel()

        scope.launch {
            while (true) {
                val packet = PacketHeader.Uncompressed.receive(readChannel, protocol)

                packet?.let { packetFlow.emit(it) }
            }
        }
    }

    suspend fun onPacket(function: (MinecraftPacket) -> Unit) {
        packetFlow.collect { function(it) }
    }

    suspend fun send(packet: MinecraftPacket) {
        PacketHeader.Uncompressed.send(packet, writeChannel, protocol)
    }

    fun disconnect() {
        scope.cancel() // Cancel the coroutine scope
        socket.close()
        selectorManager.close()
    }
}