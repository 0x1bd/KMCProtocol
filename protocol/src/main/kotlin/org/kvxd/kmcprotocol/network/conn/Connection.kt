package org.kvxd.kmcprotocol.network.conn

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.EOFException
import org.kvxd.kmcprotocol.core.CompressionSetupPacket
import org.kvxd.kmcprotocol.core.HandshakeIntentPacket
import org.kvxd.kmcprotocol.core.KmcConnectionException
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.StateTransitionPacket
import org.kvxd.kmcprotocol.network.Direction

abstract class Connection internal constructor(
    val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket,
) : AutoCloseable {

    private val writeChannel: ByteWriteChannel = socket.openWriteChannel()
    private val readChannel: ByteReadChannel = socket.openReadChannel()

    private val sendMutex = Mutex()
    private val receiveMutex = Mutex()

    private val closed = AtomicBoolean(false)
    private val closeHandlers = CopyOnWriteArrayList<() -> Unit>()

    val remoteAddress: String = runCatching { socket.remoteAddress.toString() }.getOrDefault("<unavailable>")

    val isClosed: Boolean
        get() = closed.get() || socket.isClosed

    val incoming: Flow<MinecraftPacket> = flow {
        while (true) {
            val packet = receiveOrNull() ?: break
            emit(packet)
        }
    }

    fun invokeOnClose(handler: () -> Unit) {
        if (closed.get()) {
            handler()
            return
        }
        closeHandlers += handler
        if (closed.get() && closeHandlers.remove(handler)) handler()
    }

    suspend fun send(packet: MinecraftPacket) {
        sendMutex.withLock {
            try {
                data.send(packet, writeChannel)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                throw connectionError("sending ${packet::class.qualifiedName}", cause)
            }
            applyProtocolFlow(packet)
        }
    }

    suspend fun receive(): MinecraftPacket =
        receiveOrNull() ?: throw KmcConnectionException(
            operation = "receiving packet",
            endpoint = remoteAddress,
            state = data.state,
            direction = direction,
            details = "The peer closed the connection.",
        )

    suspend fun receiveOrNull(): MinecraftPacket? =
        receiveMutex.withLock {
            var packet: MinecraftPacket? = null
            while (packet == null) {
                packet = try {
                    data.receive(readChannel, direction)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: EOFException) {
                    return@withLock null
                } catch (cause: ClosedReceiveChannelException) {
                    return@withLock null
                } catch (cause: Throwable) {
                    if (cause.isEndOfStream()) return@withLock null
                    throw connectionError("receiving packet", cause)
                }
            }
            applyProtocolFlow(packet)
            packet
        }

    suspend fun flushAndClose() {
        if (closed.get()) return
        runCatching { sendMutex.withLock { writeChannel.flushAndClose() } }
        close()
    }

    fun disconnect() = close()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runCatching { readChannel.cancel() }
        runCatching { socket.close() }

        closeHandlers.forEach { handler -> runCatching { handler() } }
        closeHandlers.clear()
    }

    private fun Throwable.isEndOfStream(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is EOFException || current is ClosedReceiveChannelException) return true
            current = current.cause.takeIf { it !== current }
        }
        return false
    }

    private fun connectionError(operation: String, cause: Throwable): Throwable {
        if (cause is KmcConnectionException) return cause
        return KmcConnectionException(
            operation = operation,
            endpoint = remoteAddress,
            state = data.state,
            direction = direction,
            details = cause.message ?: cause::class.simpleName.orEmpty(),
            cause = cause,
        )
    }

    private fun applyProtocolFlow(packet: MinecraftPacket) {
        if (!data.automaticProtocolFlow) return

        when (packet) {
            is HandshakeIntentPacket -> data.switchTo(nextProtocolState(packet.nextProtocolStateId))
            is CompressionSetupPacket -> data.setCompression(packet.compressionThreshold)
            is StateTransitionPacket -> data.switchTo(packet.targetProtocolState)
        }
    }

    private fun nextProtocolState(nextState: Int): ProtocolState =
        when (nextState) {
            1 -> ProtocolState.Status
            2, 3 -> ProtocolState.Login
            else -> data.state
        }
}
