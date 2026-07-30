package org.kvxd.kmcprotocol.conformance

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCustomPayloadPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.serverbound.ServerboundCustomPayloadPacket
import org.kvxd.kmcprotocol.sampleGeneratedPacket
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class VanillaCodecConformanceTest {

    @Test
    fun `generated packets encode exactly as vanilla re-encodes them`() = runBlocking {
        if (!FabricServerFixture.enabled()) return@runBlocking

        FabricServerFixture.withServer { host, port ->
            val version = Protocols.V1_21_4
            val client = Client.connect(InetSocketAddress(host, port), version.protocolData())
            val mismatches = mutableListOf<String>()
            val rejected = mutableListOf<String>()
            val absent = mutableListOf<String>()
            val trailing = mutableListOf<String>()
            var matched = 0

            try {
                client.loginToConfiguration(host, port)

                ProtocolState.entries.forEach { state ->
                    version.registrySource.packetsById(state).values.flatMap { it.values }.forEach { packetClass ->
                        val metadata = packetClass.findAnnotation<PacketMetadata>()
                        assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")

                        val serializer = version.protocolData(state).registry
                            .getPacketSerializerByClass(state, packetClass)
                        assertNotNull(serializer, "${packetClass.qualifiedName} is not registered")

                        val ours = runCatching {
                            encodePacket(state, sampleGeneratedPacket(serializer, version))
                        }.getOrElse {
                            rejected += "${packetClass.simpleName}: local encode ${it::class.simpleName}: ${it.message}"
                            return@forEach
                        }

                        val request = ByteArray(ours.size + 2)
                        request[0] = state.ordinal.toByte()
                        request[1] = if (metadata.direction == Direction.Serverbound) 0 else 1
                        ours.copyInto(request, 2)

                        client.send(ServerboundCustomPayloadPacket(VanillaProbeChannel, request))
                        val ack = withTimeout(60.seconds) { client.awaitPayload(VanillaAckChannel) }

                        if (ack.data.isEmpty()) {
                            rejected += "${packetClass.simpleName}: empty ack"
                            return@forEach
                        }

                        if (ack.data[0].toInt() != 0) {
                            val reason = String(ack.data, 1, ack.data.size - 1, Charsets.UTF_8)
                            when {
                                reason.contains("unknown packet id") ->
                                    absent += "${packetClass.simpleName}: $reason"
                                reason.contains("trailing bytes") ->
                                    trailing += "${packetClass.simpleName}: $reason ours=${ours.joinToString("") { b -> "%02x".format(b) }}"
                                else -> rejected += "${packetClass.simpleName}: $reason"
                            }
                            return@forEach
                        }

                        val vanilla = ack.data.copyOfRange(1, ack.data.size)
                        if (!vanilla.contentEquals(ours)) {
                            mismatches += "${packetClass.simpleName}: ours=${ours.size}B vanilla=${vanilla.size}B " +
                                "oursHex=${ours.toHex()} vanillaHex=${vanilla.toHex()}"
                        } else {
                            matched += 1
                        }
                    }
                }
            } finally {
                client.close()
            }

            println(
                "[vanilla-codec] byte-identical=$matched mismatched=${mismatches.size} " +
                    "trailing=${trailing.size} rejectedSample=${rejected.size} notInVanilla=${absent.size}"
            )
            trailing.forEach { println("[vanilla-codec] TRAILING $it") }
            mismatches.forEach { println("[vanilla-codec] MISMATCH $it") }
            rejected.take(30).forEach { println("[vanilla-codec] sample-rejected $it") }
            absent.take(10).forEach { println("[vanilla-codec] not-in-vanilla $it") }

            assertTrue(matched > 0, "Vanilla accepted no packets at all; the probe harness is broken")
            assertTrue(
                trailing.isEmpty(),
                "${trailing.size} packets encoded more bytes than vanilla consumed: ${trailing.take(5)}",
            )
            val unexpected = mismatches.filterNot { entry ->
                CanonicalisingPackets.any { entry.startsWith("$it:") }
            }
            assertTrue(
                unexpected.isEmpty(),
                "${unexpected.size} packets re-encoded differently for reasons other than known vanilla " +
                    "canonicalisation: ${unexpected.take(5)}",
            )
        }
    }

    private suspend fun Client.awaitPayload(channel: String): ClientboundCustomPayloadPacket {
        while (true) {
            val packet = receive()
            if (packet is ClientboundCustomPayloadPacket && packet.channel == channel) return packet
        }
    }

    private suspend fun encodePacket(state: ProtocolState, packet: MinecraftPacket): ByteArray {
        val data = Protocols.V1_21_4.protocolData(state)
        val channel = ByteChannel()
        data.send(packet, channel)
        channel.close()
        val framed = channel.readRemaining().readByteArray()
        return framed.stripFrameLength()
    }

    private fun ByteArray.stripFrameLength(): ByteArray {
        var index = 0
        while (index < size && this[index].toInt() and 0x80 != 0) index += 1
        return copyOfRange(index + 1, size)
    }

    private fun ByteArray.toHex(): String =
        take(64).joinToString("") { "%02x".format(it) } + if (size > 64) "..." else ""

    private companion object {
        const val VanillaProbeChannel = "kmcprotocol:vanilla_probe"
        const val VanillaAckChannel = "kmcprotocol:vanilla_ack"

        val CanonicalisingPackets = setOf(
            "ClientboundDisconnectPacket",
            "ClientboundUpdateLightPacket",
            "ServerboundUpdateJigsawBlockPacket",
        )
    }
}
