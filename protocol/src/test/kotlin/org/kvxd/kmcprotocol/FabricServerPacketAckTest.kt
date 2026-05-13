package org.kvxd.kmcprotocol

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.ProtocolValue
import org.kvxd.kmcprotocol.core.encoding.readVarInt
import org.kvxd.kmcprotocol.packets.generated.configuration.serverbound.ServerboundCustomPayloadPacket
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginAcknowledgedPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginStartPacket
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class FabricServerPacketAckTest {

    @Test
    fun `fabric test mod acknowledges a generated configuration custom payload`() = runBlocking {
        if (!fabricServerEnabled()) return@runBlocking

        val host = setting("kmc.fabricServer.host") ?: "127.0.0.1"
        val port = setting("kmc.fabricServer.port")?.toInt() ?: 25567
        val nonce = "probe-${System.nanoTime()}"
        val probeBytes = byteArrayOf(1, 2, 3, 4)

        val selector = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selector).tcp().connect(InetSocketAddress(host, port))
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        val data = ProtocolData()

        try {
            data.format.send(ServerboundSetProtocolPacket(769, host, port, 2), output)

            data.state = ProtocolState.Login
            data.format.send(
                ServerboundLoginStartPacket(
                    username = "KmcPacketTest",
                    playerUUID = ProtocolValue("00000000-0000-0000-0000-000000000001"),
                ),
                output,
            )

            withTimeout(20_000) { awaitPacketId(input, expectedId = 0x02) }
            data.format.send(ServerboundLoginAcknowledgedPacket, output)

            data.state = ProtocolState.Configuration
            data.format.send(
                ServerboundCustomPayloadPacket(
                    channel = "kmcprotocol:probe",
                    data = ProtocolValue.bytes(encodeProbe(nonce, probeBytes)),
                ),
                output,
            )

            val ack = withTimeout(20_000) { awaitConfigurationAck(input) }
            assertEquals("OK:$nonce:${probeBytes.size}", ack)
        } finally {
            socket.close()
            selector.close()
        }
    }

    private suspend fun awaitPacketId(input: ByteReadChannel, expectedId: Int) {
        while (true) {
            val frame = readFrame(input)
            val packet = ByteReadChannel(frame)
            val id = packet.readVarInt()
            if (id == expectedId) return
        }
    }

    private suspend fun awaitConfigurationAck(input: ByteReadChannel): String {
        while (true) {
            val frame = readFrame(input)
            val packet = ByteReadChannel(frame)
            val id = packet.readVarInt()
            if (id != 0x01) continue

            val channel = packet.readProtocolString()
            if (channel != "kmcprotocol:ack") continue

            return packet.readProtocolString()
        }
    }

    private suspend fun readFrame(input: ByteReadChannel): ByteArray {
        val length = input.readVarInt()
        val frame = ByteArray(length)
        input.readFully(frame)
        return frame
    }

    private fun encodeProbe(nonce: String, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeProtocolString(nonce)
        output.writeVarInt(bytes.size)
        output.write(bytes)
        return output.toByteArray()
    }

    private suspend fun ByteReadChannel.readProtocolString(): String {
        val bytes = ByteArray(readVarInt())
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun ByteArrayOutputStream.writeProtocolString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var current = value
        do {
            val byte = current and 0x7f
            current = current ushr 7
            write(if (current != 0) byte or 0x80 else byte)
        } while (current != 0)
    }

    private fun fabricServerEnabled(): Boolean =
        setting("kmc.fabricServer.enabled")?.toBooleanStrictOrNull() == true

    private fun setting(name: String): String? =
        System.getProperty(name)?.takeIf(String::isNotBlank)
            ?: System.getenv(name.uppercase().replace('.', '_'))?.takeIf(String::isNotBlank)
}
