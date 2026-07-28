package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCommonSelectKnownPacksPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.play.serverbound.ServerboundQueryBlockNbtPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.types.Position
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodecPerformanceTest {

    @Test
    fun `encode and decode sustain a usable packet rate`() {
        val version = Protocols.V1_21_4
        val data = version.protocolData(initialState = ProtocolState.Play, automaticProtocolFlow = false)
        val packet = ServerboundQueryBlockNbtPacket(transactionId = 7, location = Position(12, -34, 56))
        val serializer = data.registry.getPacketSerializerByClass(ProtocolState.Play, packet::class)!!

        repeat(Warmup) { roundTrip(serializer, packet, version) }

        val iterations = 200_000
        val nanos = measureNanoTime { repeat(iterations) { roundTrip(serializer, packet, version) } }
        val perSecond = iterations * 1_000_000_000L / nanos.coerceAtLeast(1)

        println("[codec-perf] bitfield packet round trips/sec: $perSecond")
        assertTrue(perSecond > 100_000, "Codec managed only $perSecond round trips/sec")
    }

    @Test
    fun `dynamic and native paths both stay off the pathological path`() {
        val version = Protocols.V1_21_4
        val data = version.protocolData(initialState = ProtocolState.Configuration, automaticProtocolFlow = false)
        val packet = ClientboundCommonSelectKnownPacksPacket(
            packs = List(16) { ClientboundCommonSelectKnownPacksPacket.Pack("minecraft", "core-$it", "1.21.4") },
        )
        val serializer = data.registry.getPacketSerializerByClass(ProtocolState.Configuration, packet::class)!!

        repeat(Warmup) { roundTrip(serializer, packet, version) }

        val iterations = 50_000
        val nanos = measureNanoTime { repeat(iterations) { roundTrip(serializer, packet, version) } }
        val perSecond = iterations * 1_000_000_000L / nanos.coerceAtLeast(1)

        println("[codec-perf] 16-element array packet round trips/sec: $perSecond")
        assertTrue(perSecond > 20_000, "Codec managed only $perSecond round trips/sec")
    }

    private fun <T : org.kvxd.kmcprotocol.core.MinecraftPacket> roundTrip(
        serializer: kotlinx.serialization.KSerializer<T>,
        packet: T,
        version: org.kvxd.kmcprotocol.core.ProtocolVersion,
    ) {
        val writer = PacketWriter()
        serializer.serialize(MinecraftEncoder(writer, version.codec), packet)
        val decoded = serializer.deserialize(MinecraftDecoder(PacketReader(writer.toByteArray()), version.codec))
        assertEquals(packet, decoded)
    }

    private companion object {
        const val Warmup = 20_000
    }
}
