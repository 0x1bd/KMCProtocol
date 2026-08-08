package org.kvxd.kmcprotocol.network.middleware

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.test.runTest
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.Direction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        )
        val serverbound = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Serverbound)
        val clientbound = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        pipeline.outbound(clientbound)
        assertEquals(listOf("inner:out", "outer:out"), calls)

        calls.clear()
        pipeline.inbound(serverbound)
        assertEquals(listOf("outer:in", "inner:in"), calls)
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
        val pipeline = ProtocolMiddlewarePipeline(listOf(expanding, suffixing), context)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        val transformed = pipeline.outbound(packet)

        assertEquals(2, transformed.size)
        assertContentEquals(byteArrayOf(0, 1, 9), transformed[0].bytes)
        assertContentEquals(byteArrayOf(0, 2, 9), transformed[1].bytes)
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
        val pipeline = ProtocolMiddlewarePipeline(listOf(cancelling, outer), context)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Clientbound)

        assertEquals(emptyList(), pipeline.outbound(packet))
        assertEquals(false, reachedOuter)
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
        val pipeline = ProtocolMiddlewarePipeline(listOf(invalid), context)
        val packet = RawProtocolPacket(byteArrayOf(0), ProtocolState.Handshake, Direction.Serverbound)

        assertFailsWith<KmcProtocolException> { pipeline.inbound(packet) }
    }

    @Test
    fun `sessions are connection local and close in reverse order`() {
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

        val first = ProtocolMiddlewarePipeline(listOf(middleware, middleware), context)
        val second = ProtocolMiddlewarePipeline(listOf(middleware), context)

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
