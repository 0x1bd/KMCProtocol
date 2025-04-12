package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.core.ProtocolData
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.EOFException

abstract class Connection(
    private val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket
) {

    private val writeChannel: ByteWriteChannel
    private val readChannel: ByteReadChannel

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("session/${socket.remoteAddress}"))

    val remoteAddress: SocketAddress
        get() = socket.remoteAddress

    val connected: Boolean
        get() = !socket.isClosed && !writeChannel.isClosedForWrite && !readChannel.isClosedForRead

    private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    init {
        writeChannel = socket.openWriteChannel()
        readChannel = socket.openReadChannel()

        scope.launch {
            while (connected) {
                try {
                    val packet = data.format.receive(readChannel.readRemaining(), direction)

                    if (packet == null) {
                        _events.emit(ConnectionEvent.Disconnected)
                        break
                    }

                    _events.emit(ConnectionEvent.PacketReceived(packet))
                } catch (e: Exception) {
                    println(e)
                    if (e is EOFException) {
                        _events.emit(ConnectionEvent.Disconnected)
                    } else {
                        _events.emit(ConnectionEvent.ConnectionError(e))
                    }
                }
            }
        }

    }

    fun disconnect() = runBlocking {
        writeChannel.flushAndClose()
        readChannel.cancel()

        socket.close()
        _events.emit(ConnectionEvent.Disconnected)
    }

}