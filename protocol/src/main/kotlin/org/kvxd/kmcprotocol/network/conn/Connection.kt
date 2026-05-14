package org.kvxd.kmcprotocol.network.conn

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.KmcConnectionException
import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import kotlinx.io.EOFException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Packet-oriented wrapper around a connected Minecraft socket.
 */
abstract class Connection(
    /**
     * Protocol context used by this connection.
     */
    val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket,
) : CoroutineScope, AutoCloseable {

    private val connectionJob = SupervisorJob()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + connectionJob

    private val writeChannel = socket.openWriteChannel()
    private val readChannel = socket.openReadChannel()

    private val remoteEndpoint = runCatching { socket.remoteAddress.toString() }.getOrDefault("<unavailable>")

    /**
     * Remote socket endpoint rendered as text.
     */
    val remoteAddress: String
        get() = remoteEndpoint

    private val closed = AtomicBoolean(false)

    private val incomingPackets = Channel<MinecraftPacket>(Channel.BUFFERED)
    /**
     * Stream of packets received in the configured direction.
     */
    val incoming: ReceiveChannel<MinecraftPacket> = incomingPackets

    private val connectionErrors = Channel<Throwable>(Channel.BUFFERED)

    /**
     * Stream of asynchronous connection errors.
     */
    val errors: ReceiveChannel<Throwable> = connectionErrors

    init {
        launch {
            while (!socket.isClosed && !writeChannel.isClosedForWrite && !readChannel.isClosedForRead) {
                try {
                    val packet = data.receive(readChannel, direction) ?: continue
                    applyIncomingFlow(packet)

                    incomingPackets.send(packet)
                } catch (e: Exception) {
                    when (e) {
                        is EOFException -> disconnect()
                        else -> {
                            connectionErrors.trySend(connectionError("receiving packet", e))
                            disconnect()
                        }
                    }
                }
            }
        }
    }

    /**
     * Encodes and sends a packet, then applies automatic protocol flow when enabled.
     */
    suspend fun send(packet: MinecraftPacket) {
        try {
            data.send(packet, writeChannel)
            applyOutgoingFlow(packet)
        } catch (cause: Throwable) {
            val error = connectionError("sending ${packet::class.qualifiedName}", cause)
            connectionErrors.trySend(error)
            throw error
        }
    }

    /**
     * Receives the next packet or throws the next connection error.
     */
    suspend fun receive(): MinecraftPacket =
        select {
            incomingPackets.onReceive { it }
            connectionErrors.onReceive { throw it }
        }

    /**
     * Closes this connection.
     */
    fun disconnect() = close()

    /**
     * Closes channels, the socket, and any transport resources owned by the connection.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        closeChannels()
    }

    private fun closeChannels() = runBlocking {
        try {
            writeChannel.flushAndClose()
        } finally {
            readChannel.cancel()
            socket.close()
            closeTransportResources()
            incomingPackets.close()
            connectionErrors.close()
            connectionJob.cancel()
        }
    }

    protected open fun closeTransportResources() {}

    private fun connectionError(operation: String, cause: Throwable): Throwable {
        if (cause is KmcConnectionException) return cause
        if (cause is KmcProtocolException) {
            return KmcConnectionException(
                operation = operation,
                endpoint = remoteEndpoint,
                state = data.state,
                direction = direction,
                details = cause.message ?: cause::class.simpleName.orEmpty(),
                cause = cause,
            )
        }
        return KmcConnectionException(
            operation = operation,
            endpoint = remoteEndpoint,
            state = data.state,
            direction = direction,
            details = cause.message ?: cause::class.simpleName.orEmpty(),
            cause = cause,
        )
    }

    private fun applyIncomingFlow(packet: MinecraftPacket) {
        if (!data.automaticProtocolFlow) return
        applyProtocolFlow(packet)
    }

    private fun applyOutgoingFlow(packet: MinecraftPacket) {
        if (!data.automaticProtocolFlow) return
        applyProtocolFlow(packet)
    }

    private fun applyProtocolFlow(packet: MinecraftPacket) {
        val className = packet::class.simpleName.orEmpty()
        val packetDirection = packet::class.findAnnotation<PacketMetadata>()?.direction
        when {
            packetDirection == Direction.Serverbound && className.endsWith("SetProtocolPacket") ->
                data.switchTo(nextProtocolState(packet.readIntProperty("nextState")))
            packetDirection == Direction.Clientbound && className.endsWith("CompressPacket") ->
                data.enableCompression(packet.readIntProperty("threshold"))
            packetDirection == Direction.Serverbound && className.endsWith("LoginAcknowledgedPacket") ->
                data.switchTo(ProtocolState.Configuration)
            packetDirection == Direction.Serverbound && className.endsWith("FinishConfigurationPacket") ->
                data.switchTo(ProtocolState.Play)
        }
    }

    private fun nextProtocolState(nextState: Int): ProtocolState =
        when (nextState) {
            1 -> ProtocolState.Status
            2 -> ProtocolState.Login
            else -> data.state
        }

    private fun MinecraftPacket.readIntProperty(name: String): Int =
        this::class.memberProperties
            .single { it.name == name }
            .getter
            .call(this) as Int
}
