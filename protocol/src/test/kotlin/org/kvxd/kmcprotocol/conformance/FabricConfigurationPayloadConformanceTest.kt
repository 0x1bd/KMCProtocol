package org.kvxd.kmcprotocol.conformance

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.ktor.network.sockets.InetSocketAddress
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.configuration.clientbound.ClientboundCustomPayloadPacket
import org.kvxd.kmcprotocol.packets.generated.configuration.serverbound.ServerboundCustomPayloadPacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.seconds

class FabricConfigurationPayloadConformanceTest {

    @Test
    fun `configuration custom payload round trips through kmcp packet serialization`() = runBlocking {
        if (!FabricServerFixture.enabled()) return@runBlocking

        FabricServerFixture.withServer { host, port ->
            val client = Client(InetSocketAddress(host, port))
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

    private suspend fun Client.awaitCustomPayload(channel: String): ClientboundCustomPayloadPacket {
        while (true) {
            val packet = receive()
            if (packet is ClientboundCustomPayloadPacket && packet.channel == channel) return packet
        }
    }
}
