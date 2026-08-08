package org.kvxd.kmcprotocol

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.test.runTest
import org.kvxd.kmcprotocol.core.KmcPacketDecodeException
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolLimits
import org.kvxd.kmcprotocol.core.encoding.DynamicValue
import net.lenni0451.mcstructs.nbt.NbtType
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.core.encoding.readNbtPayload
import org.kvxd.kmcprotocol.core.encoding.writeNbtString
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProtocolHardeningTest {

    @Test
    fun `oversized frame length is rejected before allocating`() = runTest {
        val data = Protocols.V1_21_4.protocolData()
        val channel = ByteChannel()

        val frame = PacketWriter().apply { writeVarInt(ProtocolLimits.DefaultMaxFrameLength + 1) }.toByteArray()
        channel.writeFully(frame)
        channel.flush()

        val failure = assertFailsWith<KmcPacketDecodeException> {
            data.receive(channel, Direction.Clientbound)
        }
        assertTrue(failure.message!!.contains("outside the permitted range"), failure.message!!)
    }

    @Test
    fun `oversized declared uncompressed length is rejected`() = runTest {
        val data = Protocols.V1_21_4.protocolData()
        data.setCompression(1)

        val payload = PacketWriter().apply {
            writeVarInt(ProtocolLimits.DefaultMaxUncompressedLength + 1)
            writeBytes(ByteArray(8))
        }.toByteArray()

        val channel = ByteChannel()
        channel.writeFully(
            PacketWriter().apply {
                writeVarInt(payload.size)
                writeBytes(payload)
            }.toByteArray()
        )
        channel.flush()

        val failure = assertFailsWith<KmcPacketDecodeException> {
            data.receive(channel, Direction.Clientbound)
        }
        assertTrue(failure.message!!.contains("uncompressed length"), failure.message!!)
    }

    @Test
    fun `a zero length frame is rejected like vanilla`() = runTest {
        val data = Protocols.V1_21_4.protocolData()
        val channel = ByteChannel()
        channel.writeFully(PacketWriter().apply { writeVarInt(0) }.toByteArray())
        channel.flush()

        val failure = assertFailsWith<KmcPacketDecodeException> {
            data.receive(channel, Direction.Clientbound)
        }
        assertTrue(failure.message!!.contains("Frame length 0"), failure.message!!)
    }

    @Test
    fun `a compressed packet below the negotiated threshold is rejected when validating`() = runTest {
        val data = Protocols.V1_21_4.protocolData(
            limits = ProtocolLimits(validateDecompressedSize = true),
        )
        data.setCompression(256)

        val payload = PacketWriter().apply {
            writeVarInt(8)
            writeBytes(ByteArray(4))
        }.toByteArray()

        val channel = ByteChannel()
        channel.writeFully(
            PacketWriter().apply {
                writeVarInt(payload.size)
                writeBytes(payload)
            }.toByteArray()
        )
        channel.flush()

        val failure = assertFailsWith<KmcPacketDecodeException> {
            data.receive(channel, Direction.Clientbound)
        }
        assertTrue(failure.message!!.contains("below the negotiated compression threshold"), failure.message!!)
    }

    @Test
    fun `a negative compression threshold disables compression`() {
        val data = Protocols.V1_21_4.protocolData()

        data.setCompression(256)
        assertTrue(data.isCompressionEnabled)

        data.setCompression(-1)
        assertFalse(data.isCompressionEnabled)
    }

    @Test
    fun `nbt nesting past the depth limit is rejected`() {
        val depth = ProtocolLimits.DefaultMaxNbtDepth + 32
        val writer = PacketWriter()
        repeat(depth) {
            writer.writeByte(NbtType.COMPOUND.id.toByte())
            writer.writeNbtString("n")
        }
        repeat(depth + 1) { writer.writeByte(NbtType.END.id.toByte()) }

        val reader = PacketReader(writer.toByteArray())
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.COMPOUND) }
        assertTrue(failure.message!!.contains("depth"), failure.message!!)
    }

    @Test
    fun `a configured nbt depth limit below the default is enforced`() {
        val depth = 32
        val writer = PacketWriter()
        repeat(depth) {
            writer.writeByte(NbtType.COMPOUND.id.toByte())
            writer.writeNbtString("n")
        }
        repeat(depth + 1) { writer.writeByte(NbtType.END.id.toByte()) }

        val reader = PacketReader(writer.toByteArray(), limits = ProtocolLimits(maxNbtDepth = 4))
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.COMPOUND) }
        assertTrue(failure.message!!.contains("maximum depth of 4"), failure.message!!)
    }

    @Test
    fun `an nbt array is bounded by the remaining bytes and not only by the byte budget`() {
        val writer = PacketWriter()
        writer.writeInt(1_000_000)
        writer.writeBytes(ByteArray(8))

        val reader = PacketReader(writer.toByteArray())
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.BYTE_ARRAY) }
        assertTrue(failure.message!!.contains("larger Nbt than allowed"), failure.message!!)
    }

    @Test
    fun `an nbt array longer than the remaining bytes is rejected`() {
        val writer = PacketWriter()
        writer.writeInt(Int.MAX_VALUE / 8)
        writer.writeBytes(ByteArray(8))

        val reader = PacketReader(writer.toByteArray())
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.LONG_ARRAY) }
        assertTrue(failure.message!!.contains("larger Nbt than allowed"), failure.message!!)
    }

    @Test
    fun `an nbt list longer than the remaining bytes is rejected`() {
        val writer = PacketWriter()
        writer.writeByte(NbtType.LONG.id.toByte())
        writer.writeInt(Int.MAX_VALUE / 4)
        writer.writeBytes(ByteArray(8))

        val reader = PacketReader(writer.toByteArray())
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.LIST) }
        assertTrue(failure.message!!.contains("larger Nbt than allowed"), failure.message!!)
    }

    @Test
    fun `an end typed nbt list must be empty`() {
        val writer = PacketWriter()
        writer.writeByte(NbtType.END.id.toByte())
        writer.writeInt(3)

        val reader = PacketReader(writer.toByteArray())
        val failure = assertFailsWith<KmcProtocolException> { reader.readNbtPayload(NbtType.LIST) }
        assertTrue(failure.message!!.contains("END"), failure.message!!)
    }

    @Test
    fun `non canonical varints are rejected`() {
        val overlong = PacketReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F))
        assertFailsWith<KmcProtocolException> { overlong.readVarInt() }

        val valid = PacketReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F))
        assertEquals(-1, valid.readVarInt())
    }

    @Test
    fun `an oversized nbt string is rejected instead of truncating its length`() {
        val writer = PacketWriter()
        val failure = assertFailsWith<KmcProtocolException> { writer.writeNbtString("a".repeat(70_000)) }
        assertTrue(failure.message!!.contains("65535"), failure.message!!)
    }

    @Test
    fun `reading past the end of a packet fails instead of returning garbage`() {
        val reader = PacketReader(byteArrayOf(1, 2, 3))
        assertFailsWith<KmcProtocolException> { reader.readBytes(64) }
        assertFailsWith<KmcProtocolException> { reader.readBytes(-1) }
    }

    @Test
    fun `dynamic value hashing does not depend on map insertion order`() {
        val left = DynamicValue.obj(
            linkedMapOf("a" to DynamicValue(1), "b" to DynamicValue(2))
        )
        val right = DynamicValue.obj(
            linkedMapOf("b" to DynamicValue(2), "a" to DynamicValue(1))
        )

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())

        val different = DynamicValue.obj(linkedMapOf("a" to DynamicValue(1), "b" to DynamicValue(3)))
        assertNotEquals(left, different)
    }

    @Test
    fun `packet limits are validated on construction`() {
        assertFailsWith<IllegalArgumentException> { ProtocolLimits(maxFrameLength = 0) }
        assertFailsWith<IllegalArgumentException> { ProtocolLimits(maxNbtDepth = 0) }
        assertFailsWith<IllegalArgumentException> { ProtocolLimits(maxNbtBytes = 0) }
        assertFailsWith<IllegalArgumentException> {
            ProtocolLimits(maxFrameLength = ProtocolLimits.MaxEncodableFrameLength + 1)
        }
    }
}
