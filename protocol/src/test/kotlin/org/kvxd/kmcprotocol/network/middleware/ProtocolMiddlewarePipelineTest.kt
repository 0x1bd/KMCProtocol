package org.kvxd.kmcprotocol.network.middleware

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolMiddlewarePipelineTest {

    private val context = ProtocolMiddlewareContext(
        protocolData = ProtocolData(),
        inboundDirection = Direction.Serverbound,
        localAddress = InetSocketAddress("127.0.0.1", 25565),
        remoteAddress = InetSocketAddress("127.0.0.1", 54321),
    )

    @Test
    fun `middleware stack uses application to wire registration order`() = runTest {
        val calls = mutableListOf<String>()
        val pipeline = ProtocolMiddlewarePipeline(
            listOf(recordingMiddleware("inner", calls), recordingMiddleware("outer", calls)),
            context,
            backgroundScope,
        )
        val serverbound = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Serverbound)
        val clientbound = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        pipeline.outbound(clientbound)
        assertEquals(listOf("inner:out", "outer:out"), calls)

        calls.clear()
        pipeline.inbound(serverbound)
        assertEquals(listOf("outer:in", "inner:in"), calls)
        pipeline.close()
    }

    @Test
    fun `packet expansion feeds every output into the next middleware`() = runTest {
        val expanding = ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> = listOf(
                    packet.withBytes(packet.bytes + 1),
                    packet.withBytes(packet.bytes + 2),
                )
            }
        }
        val suffixing = ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> =
                    listOf(packet.withBytes(packet.bytes + 9))
            }
        }
        val pipeline = ProtocolMiddlewarePipeline(listOf(expanding, suffixing), context, backgroundScope)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        val transformed = pipeline.outbound(packet)

        assertEquals(2, transformed.size)
        assertContentEquals(byteArrayOf(0, 1, 9), transformed[0].bytes)
        assertContentEquals(byteArrayOf(0, 2, 9), transformed[1].bytes)
        pipeline.close()
    }

    @Test
    fun `empty middleware result cancels packet and stops the chain`() = runTest {
        var reachedOuter = false
        val cancelling = ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> = emptyList()
            }
        }
        val outer = ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    reachedOuter = true
                    return listOf(packet)
                }
            }
        }
        val pipeline = ProtocolMiddlewarePipeline(listOf(cancelling, outer), context, backgroundScope)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        assertEquals(emptyList(), pipeline.outbound(packet))
        assertEquals(false, reachedOuter)
        pipeline.close()
    }

    @Test
    fun `middleware cannot rewrite canonical state or direction metadata`() = runTest {
        val invalid = ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> = listOf(
                    RawProtocolPacket(packet.bytes, ProtocolState.Play, Direction.Clientbound),
                )
            }
        }
        val pipeline = ProtocolMiddlewarePipeline(listOf(invalid), context, backgroundScope)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Serverbound)

        assertFailsWith<KmcProtocolException> { pipeline.inbound(packet) }
        pipeline.close()
    }

    @Test
    fun `emitted packet resumes after the middleware that emitted it`() = runTest {
        val calls = mutableListOf<String>()
        val delivered = mutableListOf<RawProtocolPacket>()
        val middle = ProtocolMiddleware { middlewareContext ->
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    calls += "middle:out"
                    middlewareContext.emitter.emitInbound(
                        RawProtocolPacket(byteArrayOf(7), packet.state, Direction.Serverbound),
                    )
                    return listOf(packet)
                }

                override suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    calls += "middle:in"
                    return listOf(packet)
                }
            }
        }
        val pipeline = ProtocolMiddlewarePipeline(
            listOf(recordingMiddleware("inner", calls), middle, recordingMiddleware("outer", calls)),
            context,
            backgroundScope,
            emittedPacketHandler = { delivered += it },
        )

        pipeline.outbound(RawProtocolPacket(byteArrayOf(1), ProtocolState.Handshake, Direction.Clientbound))

        assertEquals(listOf("inner:out", "middle:out", "outer:out", "inner:in"), calls)
        assertEquals(1, delivered.size)
        assertContentEquals(byteArrayOf(7), delivered.single().bytes)
        pipeline.close()
    }

    @Test
    fun `outbound emission only crosses middlewares still toward wire`() = runTest {
        val calls = mutableListOf<String>()
        val delivered = mutableListOf<RawProtocolPacket>()
        val middle = ProtocolMiddleware { middlewareContext ->
            object : ProtocolMiddlewareSession {
                override suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    calls += "middle:in"
                    middlewareContext.emitter.emitOutbound(
                        RawProtocolPacket(byteArrayOf(8), packet.state, Direction.Clientbound),
                    )
                    return listOf(packet)
                }
            }
        }
        val pipeline = ProtocolMiddlewarePipeline(
            listOf(recordingMiddleware("inner", calls), middle, recordingMiddleware("outer", calls)),
            context,
            backgroundScope,
            emittedPacketHandler = { delivered += it },
        )

        pipeline.inbound(RawProtocolPacket(byteArrayOf(1), ProtocolState.Handshake, Direction.Serverbound))

        assertEquals(listOf("outer:in", "middle:in", "inner:in", "outer:out"), calls)
        assertEquals(1, delivered.size)
        assertContentEquals(byteArrayOf(8), delivered.single().bytes)
        pipeline.close()
    }

    @Test
    fun `scheduled emission runs without a root packet and keeps stack position`() = runTest {
        val calls = mutableListOf<String>()
        val emitter = CompletableDeferred<ProtocolMiddlewareEmitter>()
        val delivered = CompletableDeferred<RawProtocolPacket>()
        val middle = ProtocolMiddleware { middlewareContext ->
            emitter.complete(middlewareContext.emitter)
            object : ProtocolMiddlewareSession {}
        }
        val pipeline = ProtocolMiddlewarePipeline(
            listOf(recordingMiddleware("inner", calls), middle, recordingMiddleware("outer", calls)),
            context,
            backgroundScope,
            emittedPacketHandler = { delivered.complete(it) },
        )
        pipeline.start()

        assertTrue(
            emitter.await().scheduleInbound(
                RawProtocolPacket(byteArrayOf(4), ProtocolState.Handshake, Direction.Serverbound),
            ),
        )
        val emitted = withTimeout(1_000) { delivered.await() }

        assertContentEquals(byteArrayOf(4), emitted.bytes)
        assertEquals(listOf("inner:in"), calls)
        pipeline.close()
    }

    @Test
    fun `emission from inside callback does not recursively deadlock`() = runTest {
        lateinit var emitter: ProtocolMiddlewareEmitter
        val middleware = ProtocolMiddleware { middlewareContext ->
            emitter = middlewareContext.emitter
            object : ProtocolMiddlewareSession {
                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    emitter.emitInbound(
                        RawProtocolPacket(byteArrayOf(3), packet.state, Direction.Serverbound),
                    )
                    return listOf(packet)
                }
            }
        }
        val delivered = CompletableDeferred<Unit>()
        val pipeline = ProtocolMiddlewarePipeline(
            listOf(middleware),
            context,
            backgroundScope,
            emittedPacketHandler = { delivered.complete(Unit) },
        )

        withTimeout(1_000) {
            pipeline.outbound(RawProtocolPacket(byteArrayOf(1), ProtocolState.Handshake, Direction.Clientbound))
            delivered.await()
        }
        pipeline.close()
    }

    @Test
    fun `emitter rejects the wrong flow direction`() = runTest {
        lateinit var emitter: ProtocolMiddlewareEmitter
        val middleware = ProtocolMiddleware { middlewareContext ->
            emitter = middlewareContext.emitter
            object : ProtocolMiddlewareSession {}
        }
        val pipeline = ProtocolMiddlewarePipeline(listOf(middleware), context, backgroundScope)
        pipeline.start()

        assertFailsWith<KmcProtocolException> {
            emitter.emitInbound(
                RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound),
            )
        }
        pipeline.close()
    }

    @Test
    fun `scheduled emission returns false after close`() = runTest {
        lateinit var emitter: ProtocolMiddlewareEmitter
        val middleware = ProtocolMiddleware { middlewareContext ->
            emitter = middlewareContext.emitter
            object : ProtocolMiddlewareSession {}
        }
        val pipeline = ProtocolMiddlewarePipeline(listOf(middleware), context, backgroundScope)
        pipeline.close()

        assertFalse(
            emitter.scheduleInbound(
                RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Serverbound),
            ),
        )
    }

    @Test
    fun `sessions are connection local and close in reverse order`() = runTest {
        var created = 0
        val closed = mutableListOf<Int>()
        val middleware = ProtocolMiddleware {
            val id = ++created
            object : ProtocolMiddlewareSession {
                override fun close() {
                    closed += id
                }
            }
        }

        val first = ProtocolMiddlewarePipeline(listOf(middleware, middleware), context, backgroundScope)
        val second = ProtocolMiddlewarePipeline(listOf(middleware), context, backgroundScope)

        assertEquals(3, created)
        first.close()
        second.close()
        assertEquals(listOf(2, 1, 3), closed)
    }

    private fun recordingMiddleware(name: String, calls: MutableList<String>): ProtocolMiddleware =
        ProtocolMiddleware {
            object : ProtocolMiddlewareSession {
                override suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    calls += "$name:in"
                    return listOf(packet)
                }

                override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
                    calls += "$name:out"
                    return listOf(packet)
                }
            }
        }
}