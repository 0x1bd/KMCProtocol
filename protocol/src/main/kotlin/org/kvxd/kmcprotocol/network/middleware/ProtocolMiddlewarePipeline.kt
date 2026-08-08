package org.kvxd.kmcprotocol.network.middleware

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kvxd.kmcprotocol.core.KmcProtocolException

internal class ProtocolMiddlewarePipeline(
    middlewares: List<ProtocolMiddleware>,
    context: ProtocolMiddlewareContext,
) : AutoCloseable {

    private val transformMutex = Mutex()

    private val sessions: List<ProtocolMiddlewareSession> = buildList(middlewares.size) {
        try {
            middlewares.forEach { middleware -> add(middleware.createSession(context)) }
        } catch (cause: Throwable) {
            asReversed().forEach { session -> runCatching { session.close() } }
            throw cause
        }
    }

    suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> = transformMutex.withLock {
        transform(packet, sessions) { session, current -> session.outbound(current) }
    }

    suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> = transformMutex.withLock {
        transform(packet, sessions.asReversed()) { session, current -> session.inbound(current) }
    }

    override fun close() {
        sessions.asReversed().forEach { session -> runCatching { session.close() } }
    }

    private suspend inline fun transform(
        packet: RawProtocolPacket,
        stages: List<ProtocolMiddlewareSession>,
        crossinline transform: suspend (ProtocolMiddlewareSession, RawProtocolPacket) -> List<RawProtocolPacket>,
    ): List<RawProtocolPacket> {
        var packets = listOf(packet)

        for (stage in stages) {
            if (packets.isEmpty()) return emptyList()

            packets = buildList {
                for (current in packets) {
                    for (transformed in transform(stage, current)) {
                        if (transformed.state != current.state || transformed.direction != current.direction) {
                            throw KmcProtocolException(
                                "Protocol middleware changed canonical packet metadata from " +
                                    "${current.state}/${current.direction} to " +
                                    "${transformed.state}/${transformed.direction}. " +
                                    "Middlewares may transform packet bytes, but KMCP owns state and direction.",
                            )
                        }
                        add(transformed)
                    }
                }
            }
        }

        return packets
    }
}
