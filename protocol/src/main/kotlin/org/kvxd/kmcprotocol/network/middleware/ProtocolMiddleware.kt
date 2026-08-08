package org.kvxd.kmcprotocol.network.middleware

import io.ktor.network.sockets.SocketAddress
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction

fun interface ProtocolMiddleware {
    fun createSession(context: ProtocolMiddlewareContext): ProtocolMiddlewareSession
}

class ProtocolMiddlewareContext(
    val protocolData: ProtocolData,
    val inboundDirection: Direction,
    val localAddress: SocketAddress,
    val remoteAddress: SocketAddress,
) {
    val outboundDirection: Direction
        get() = inboundDirection.opposite()
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

internal fun Direction.opposite(): Direction =
    when (this) {
        Direction.Serverbound -> Direction.Clientbound
        Direction.Clientbound -> Direction.Serverbound
    }
