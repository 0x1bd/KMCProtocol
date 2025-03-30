package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.EventBus
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.EOFException

abstract class Connection(
    private val protocol: MinecraftProtocol,
    private val direction: Direction,
    private val socket: Socket
) {

    val bus = EventBus()

    private val writeChannel: ByteWriteChannel
    private val readChannel: ByteReadChannel

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("session/${socket.remoteAddress}"))

    val remoteAddress = socket.remoteAddress

    init {
        bus.onError = { cause -> cause.printStackTrace() }

        writeChannel = socket.openWriteChannel()
        readChannel = socket.openReadChannel()

        scope.launch {
            while (!socket.isClosed && !writeChannel.isClosedForWrite && !readChannel.isClosedForRead) {
                try {
                    val packet = protocol.packetFormat.receive(readChannel, protocol, direction)

                    if (packet == null) {
                        disconnect()
                        break
                    }

                    bus.post(PacketReceived(packet))
                } catch (e: Exception) {
                    if (e is EOFException) {
                        bus.post(Disconnected)
                    } else {
                        bus.post(ConnectionError(e))
                    }
                }
            }
        }
    }

    fun state(state: ProtocolState) {
        protocol.state = state
    }

    suspend fun send(packet: MinecraftPacket) {
        protocol.packetFormat.send(packet, writeChannel, protocol)
    }

    fun disconnect() = runBlocking {
        writeChannel.close()
        readChannel.cancel(null)
        socket.close()

        bus.post(Disconnected)
    }

}