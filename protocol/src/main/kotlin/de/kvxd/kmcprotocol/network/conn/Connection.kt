package de.kvxd.kmcprotocol.network.conn

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.EOFException

abstract class Connection(
    val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket,
) : CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {

    private val writeChannel = socket.openWriteChannel()
    private val readChannel = socket.openReadChannel()

    val remoteAddress
        get() = socket.remoteAddress

    private val callbacks = mutableSetOf<ConnectionCallback>()

    fun addCallback(callback: ConnectionCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: ConnectionCallback) {
        callbacks.remove(callback)
    }

    init {
        launch {
            while (!socket.isClosed && !writeChannel.isClosedForWrite && !readChannel.isClosedForRead) {
                try {
                    val packet = data.format.receive(readChannel, direction) ?: continue

                    callbacks.forEach { it.onPacketReceived(packet) }
                } catch (e: Exception) {
                    when (e) {
                        is EOFException -> disconnect()
                        else -> callbacks.forEach { it.onError(e) }
                    }
                }
            }
        }
    }

    suspend fun send(packet: MinecraftPacket) {
        data.format.send(packet, writeChannel)
    }

    fun disconnect() {
        callbacks.forEach { it.onDisconnect() }

        close()
    }

    internal fun close() = runBlocking {
        writeChannel.flushAndClose()
        readChannel.cancel()

        socket.close()
    }

}