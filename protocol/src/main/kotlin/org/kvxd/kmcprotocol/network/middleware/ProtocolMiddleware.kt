package org.kvxd.kmcprotocol.network.middleware

import io.ktor.network.sockets.SocketAddress
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction

fun interface ProtocolMiddleware {
    fun createSession(context: ProtocolMiddlewareContext): ProtocolMiddlewareSession
}

class ProtocolMiddlewareContext private constructor(
    val protocolData: ProtocolData,
    val inboundDirection: Direction,
    val localAddress: SocketAddress,
    val remoteAddress: SocketAddress,
    val emitter: ProtocolMiddlewareEmitter,
) {
    constructor(
        protocolData: ProtocolData,
        inboundDirection: Direction,
        localAddress: SocketAddress,
        remoteAddress: SocketAddress,
    ) : this(
        protocolData = protocolData,
        inboundDirection = inboundDirection,
        localAddress = localAddress,
        remoteAddress = remoteAddress,
        emitter = UnavailableProtocolMiddlewareEmitter,
    )

    val outboundDirection: Direction
        get() = inboundDirection.opposite()

    internal fun withEmitter(emitter: ProtocolMiddlewareEmitter): ProtocolMiddlewareContext =
        ProtocolMiddlewareContext(
            protocolData = protocolData,
            inboundDirection = inboundDirection,
            localAddress = localAddress,
            remoteAddress = remoteAddress,
            emitter = emitter,
        )
}

interface ProtocolMiddlewareEmitter {
    suspend fun emitInbound(packet: RawProtocolPacket)

    suspend fun emitOutbound(packet: RawProtocolPacket)

    fun scheduleInbound(packet: RawProtocolPacket): Boolean

    fun scheduleOutbound(packet: RawProtocolPacket): Boolean
}

interface ProtocolMiddlewareSession : AutoCloseable {
    suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> = listOf(packet)

    suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> = listOf(packet)

    override fun close() = Unit
}

class RawProtocolPacket(
    val bytes: ByteArray,
    val state: ProtocolState,
    val direction: Direction,
) {
    fun withBytes(bytes: ByteArray): RawProtocolPacket = RawProtocolPacket(bytes, state, direction)
}

private object UnavailableProtocolMiddlewareEmitter : ProtocolMiddlewareEmitter {
    override suspend fun emitInbound(packet: RawProtocolPacket): Nothing = unavailable()

    override suspend fun emitOutbound(packet: RawProtocolPacket): Nothing = unavailable()

    override fun scheduleInbound(packet: RawProtocolPacket): Boolean = false

    override fun scheduleOutbound(packet: RawProtocolPacket): Boolean = false

    private fun unavailable(): Nothing = throw KmcProtocolException(
        "ProtocolMiddlewareContext.emitter is only active for a middleware session attached to a connection.",
    )
}

internal fun Direction.opposite(): Direction =
    when (this) {
        Direction.Serverbound -> Direction.Clientbound
        Direction.Clientbound -> Direction.Serverbound
    }