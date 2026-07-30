package org.kvxd.kmcprotocol.conformance

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.PacketMetadata
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.v1_21_4.GeneratedPacketRegistry
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.protocolEquals
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundCustomPayloadPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.serverbound.ServerboundCustomPayloadPacket
import org.kvxd.kmcprotocol.sampleGeneratedPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.reflect.full.findAnnotation
import kotlin.time.Duration.Companion.seconds

class FabricConfigurationPayloadConformanceTest {

    @Test
    fun `configuration custom payload round trips through kmcp packet serialization`() = runBlocking {
        if (!FabricServerFixture.enabled()) return@runBlocking

        FabricServerFixture.withServer { host, port ->
            val client = Client.connect(InetSocketAddress(host, port), Protocols.V1_21_4.protocolData())
            try {
                client.loginToConfiguration(host, port)

                val probeBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67)
                client.send(
                    ServerboundCustomPayloadPacket(
                        channel = "kmcprotocol:probe",
                        data = probeBytes,
                    )
                )

                val ack = withTimeout(30.seconds) { client.awaitCustomPayload("kmcprotocol:ack") }
                assertContentEquals(probeBytes, ack.data)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `generated packet samples round trip through fabric payload echo`() = runBlocking {
        if (!FabricServerFixture.enabled()) return@runBlocking

        FabricServerFixture.withServer { host, port ->
            val client = Client.connect(InetSocketAddress(host, port), Protocols.V1_21_4.protocolData())
            try {
                client.loginToConfiguration(host, port)

                var samples = 0
                ProtocolState.entries.forEach { state ->
                    GeneratedPacketRegistry.packetsById(state).values.flatMap { it.values }.forEach { packetClass ->
                        val metadata = packetClass.findAnnotation<PacketMetadata>()
                        assertNotNull(metadata, "${packetClass.qualifiedName} is missing PacketMetadata")

                        val serializer = Protocols.V1_21_4.protocolData(state).registry
                            .getPacketSerializerByClass(state, packetClass)
                        assertNotNull(serializer, "${packetClass.qualifiedName} is not registered")

                        val packet = sampleGeneratedPacket(serializer, Protocols.V1_21_4)
                        val encoded = encodeGeneratedPacket(state, packet)
                        client.send(ServerboundCustomPayloadPacket("kmcprotocol:probe", encoded))

                        val ack = withTimeout(30.seconds) { client.awaitCustomPayload("kmcprotocol:ack") }
                        assertContentEquals(encoded, ack.data, "${packetClass.qualifiedName} payload changed in Fabric echo")

                        val decoded = Protocols.V1_21_4.protocolData(state)
                            .receive(ByteReadChannel(ack.data), metadata.direction)
                        assertTrue(
                            packet.protocolEquals(decoded),
                            "${packetClass.qualifiedName} decoded to a different packet after Fabric echo: expected=$packet actual=$decoded",
                        )
                        samples += 1
                    }
                }

                assertTrue(samples > 0, "Expected to round trip generated packet samples through Fabric")
            } finally {
                client.close()
            }
        }
    }

    private suspend fun Client.awaitCustomPayload(channel: String): ClientboundCustomPayloadPacket {
        while (true) {
            val packet = receive()
            if (packet is ClientboundCustomPayloadPacket && packet.channel == channel) return packet
        }
    }

    private suspend fun encodeGeneratedPacket(
        state: ProtocolState,
        packet: MinecraftPacket,
    ): ByteArray {
        val data = Protocols.V1_21_4.protocolData(state)
        val channel = ByteChannel()
        data.send(packet, channel)
        channel.close()
        return channel.readRemaining().readByteArray()
    }
}
