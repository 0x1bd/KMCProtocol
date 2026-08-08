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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.EOFException
import org.kvxd.kmcprotocol.core.CompressionSetupPacket
import org.kvxd.kmcprotocol.core.HandshakeIntentPacket
import org.kvxd.kmcprotocol.core.KmcConnectionException
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.StateTransitionPacket
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareContext
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewarePipeline
import org.kvxd.kmcprotocol.network.middleware.RawProtocolPacket
import org.kvxd.kmcprotocol.network.middleware.opposite

private object MiddlewareEmittedPacket : MinecraftPacket

abstract class Connection internal constructor(
    val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket,
    middlewares: List<ProtocolMiddleware> = emptyList(),
) : AutoCloseable {

    private val writeChannel: ByteWriteChannel = socket.openWriteChannel()
    private val readChannel: ByteReadChannel = socket.openReadChannel()

    private val sendMutex = Mutex()
    private val receiveMutex = Mutex()
    private val inboundDecodeMutex = Mutex()

    private val closed = AtomicBoolean(false)
    private val closeHandlers = CopyOnWriteArrayList<() -> Unit>()

    private val middlewareScope: CoroutineScope? = if (middlewares.isEmpty()) {
        null
    } else {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private val middlewareIncoming: Channel<MinecraftPacket>? = if (middlewares.isEmpty()) {
        null
    } else {
        Channel(Channel.BUFFERED)
    }

    private val middlewarePipeline: ProtocolMiddlewarePipeline? = if (middlewares.isEmpty()) {
        null
    } else try {
        ProtocolMiddlewarePipeline(
            middlewares = middlewares,
            context = ProtocolMiddlewareContext(
                protocolData = data,
                inboundDirection = direction,
                localAddress = socket.localAddress,
                remoteAddress = socket.remoteAddress,
            ),
            coroutineScope = checkNotNull(middlewareScope),
            emittedPacketHandler = { packet -> handleEmittedPacket(packet) },
            failureHandler = { cause -> handleMiddlewareFailure(cause) },
        )
    } catch (cause: Throwable) {
        runCatching { middlewareScope?.cancel() }
        runCatching { readChannel.cancel() }
        runCatching { socket.close() }
        throw cause
    }

    private val middlewareReaderJob: Job? = middlewarePipeline?.let { pipeline ->
        pipeline.start()
        checkNotNull(middlewareScope).launch { pumpMiddlewareInbound() }
    }

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
                val pipeline = middlewarePipeline
                if (pipeline == null) {
                    data.send(packet, writeChannel)
                } else {
                    val rawPacket = RawProtocolPacket(
                        bytes = data.encodePacketBytes(packet),
                        state = data.state,
                        direction = direction.opposite(),
                    )
                    val transformedPackets = pipeline.outbound(rawPacket)
                    for (transformed in transformedPackets) {
                        data.sendPacketBytes(transformed.bytes, writeChannel, packet)
                    }
                }
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
            if (middlewarePipeline == null) receiveDirectUnlocked() else receiveWithMiddlewareUnlocked()
        }

    private suspend fun receiveDirectUnlocked(): MinecraftPacket? {
        var packet: MinecraftPacket? = null
        while (packet == null) {
            packet = try {
                data.receive(readChannel, direction)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: EOFException) {
                return null
            } catch (cause: ClosedReceiveChannelException) {
                return null
            } catch (cause: Throwable) {
                if (cause.isEndOfStream()) return null
                throw connectionError("receiving packet", cause)
            }
        }
        applyProtocolFlow(packet)
        return packet
    }

    private suspend fun receiveWithMiddlewareUnlocked(): MinecraftPacket? {
        val result = checkNotNull(middlewareIncoming).receiveCatching()
        result.exceptionOrNull()?.let { throw it }
        return result.getOrNull()
    }

    private suspend fun pumpMiddlewareInbound() {
        val pipeline = checkNotNull(middlewarePipeline)
        val incoming = checkNotNull(middlewareIncoming)

        try {
            while (!closed.get()) {
                val frame = data.receivePacketBytes(readChannel, direction)
                val transformedPackets = pipeline.inbound(
                    RawProtocolPacket(
                        bytes = frame.bytes,
                        state = data.state,
                        direction = direction,
                    ),
                )
                for (transformed in transformedPackets) {
                    deliverInboundPacket(transformed, frame.frameLength)
                }
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: EOFException) {
            incoming.close()
        } catch (cause: ClosedReceiveChannelException) {
            incoming.close()
        } catch (cause: Throwable) {
            if (cause.isEndOfStream()) {
                incoming.close()
            } else {
                incoming.close(connectionError("receiving packet through protocol middleware", cause))
            }
        }
    }

    private suspend fun handleEmittedPacket(packet: RawProtocolPacket) {
        when (packet.direction) {
            direction -> deliverInboundPacket(packet, frameLength = null)
            direction.opposite() -> data.sendPacketBytes(packet.bytes, writeChannel, MiddlewareEmittedPacket)
            else -> throw KmcProtocolException(
                "Protocol middleware emitted packet with unexpected direction ${packet.direction}.",
            )
        }
    }

    private suspend fun deliverInboundPacket(packet: RawProtocolPacket, frameLength: Int?) {
        if (packet.direction != direction) {
            throw KmcProtocolException(
                "Protocol middleware delivered ${packet.direction} to the inbound application path; " +
                        "expected $direction.",
            )
        }

        inboundDecodeMutex.withLock {
            val decoded = data.decodePacketBytes(packet.bytes, direction, frameLength)
            applyProtocolFlow(decoded)
            checkNotNull(middlewareIncoming).send(decoded)
        }
    }

    private fun handleMiddlewareFailure(cause: Throwable) {
        val wrapped = connectionError("applying scheduled protocol middleware", cause)
        middlewareIncoming?.close(wrapped)
        close()
    }

    suspend fun flushAndClose() {
        if (closed.get()) return
        runCatching { sendMutex.withLock { writeChannel.flushAndClose() } }
        close()
    }

    fun disconnect() = close()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runCatching { middlewarePipeline?.close() }
        runCatching { middlewareIncoming?.close() }
        runCatching { middlewareReaderJob?.cancel() }
        runCatching { middlewareScope?.cancel() }
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