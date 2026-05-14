package org.kvxd.kmcprotocol.network.conn

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.KmcConnectionException
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
import org.kvxd.kmcprotocol.packets.generated.configuration.serverbound.ServerboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.login.clientbound.ClientboundCompressPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginAcknowledgedPacket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

abstract class Connection(
    val data: ProtocolData,
    private val direction: Direction,
    private val socket: Socket,
) : CoroutineScope, AutoCloseable {

    private val connectionJob = SupervisorJob()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + connectionJob

    private val writeChannel = socket.openWriteChannel()
    private val readChannel = socket.openReadChannel()

    private val remoteEndpoint = runCatching { socket.remoteAddress.toString() }.getOrDefault("<unavailable>")

    val remoteAddress: String
        get() = remoteEndpoint

    private val closed = AtomicBoolean(false)

    private val incomingPackets = Channel<MinecraftPacket>(Channel.BUFFERED)
    val incoming: ReceiveChannel<MinecraftPacket> = incomingPackets

    private val connectionErrors = Channel<Throwable>(Channel.BUFFERED)
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

    suspend fun receive(): MinecraftPacket =
        select {
            incomingPackets.onReceive { it }
            connectionErrors.onReceive { throw it }
        }

    fun disconnect() = close()

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
        when (packet) {
            is ServerboundSetProtocolPacket -> data.switchTo(packet.nextProtocolState())
            is ClientboundCompressPacket -> data.enableCompression(packet.threshold)
            is ServerboundLoginAcknowledgedPacket -> data.switchTo(ProtocolState.Configuration)
            is ServerboundFinishConfigurationPacket -> data.switchTo(ProtocolState.Play)
        }
    }

    private fun applyOutgoingFlow(packet: MinecraftPacket) {
        when (packet) {
            is ServerboundSetProtocolPacket -> data.switchTo(packet.nextProtocolState())
            is ClientboundCompressPacket -> data.enableCompression(packet.threshold)
            is ServerboundLoginAcknowledgedPacket -> data.switchTo(ProtocolState.Configuration)
            is ServerboundFinishConfigurationPacket -> data.switchTo(ProtocolState.Play)
        }
    }

    private fun ServerboundSetProtocolPacket.nextProtocolState(): ProtocolState =
        when (nextState) {
            1 -> ProtocolState.Status
            2 -> ProtocolState.Login
            else -> data.state
        }
}