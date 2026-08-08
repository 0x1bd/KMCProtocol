package org.kvxd.kmcprotocol

import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.IntArrayTag
import net.lenni0451.mcstructs.nbt.tags.LongArrayTag
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.encoding.NbtFormat
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.core.encoding.readNamedNbtTag
import org.kvxd.kmcprotocol.core.encoding.writeNamedNbtTag
import org.kvxd.kmcprotocol.generated.Protocols
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NbtFormatTest {

    @Test
    fun `minecraft versions map onto the nbt format that introduced their tags`() {
        assertEquals(NbtFormat.V1_0_0, NbtFormat.forMinecraftVersion("1.0.0"))
        assertEquals(NbtFormat.V1_2_1, NbtFormat.forMinecraftVersion("1.2.1"))
        assertEquals(NbtFormat.V1_2_1, NbtFormat.forMinecraftVersion("1.7.10"))
        assertEquals(NbtFormat.V1_2_1, NbtFormat.forMinecraftVersion("1.11.2"))
        assertEquals(NbtFormat.V1_12, NbtFormat.forMinecraftVersion("1.12"))
        assertEquals(NbtFormat.V1_12, NbtFormat.forMinecraftVersion("1.21.4"))
        assertEquals(NbtFormat.V1_12, NbtFormat.forMinecraftVersion("26.1"))
        assertEquals(NbtFormat.Latest, NbtFormat.forMinecraftVersion("not-a-version"))
    }

    @Test
    fun `generated protocol versions carry their nbt format`() {
        assertEquals(NbtFormat.V1_2_1, Protocols.V1_7.nbtFormat)
        assertEquals(NbtFormat.V1_2_1, Protocols.V1_8.nbtFormat)
        assertEquals(NbtFormat.V1_12, Protocols.V1_12_2.nbtFormat)
        assertEquals(NbtFormat.V1_12, Protocols.V1_21_4.nbtFormat)
        assertEquals(NbtFormat.V1_12, Protocols.V26_1.nbtFormat)
    }

    @Test
    fun `long arrays round trip on 1_12 and are rejected before it`() {
        val tag = CompoundTag().addLongArray("values", 1L, 2L, 3L)

        assertEquals(tag, roundTrip(tag, NbtFormat.V1_12))

        val failure = assertFailsWith<KmcProtocolException> {
            PacketWriter(nbt = NbtFormat.V1_2_1).writeNamedNbtTag(LongArrayTag(longArrayOf(1L)))
        }
        assertEquals(true, failure.message!!.contains("long array"), failure.message!!)
    }

    @Test
    fun `int arrays round trip on 1_2_1 and are rejected before it`() {
        val tag = CompoundTag().addIntArray("values", 1, 2, 3)

        assertEquals(tag, roundTrip(tag, NbtFormat.V1_2_1))

        val failure = assertFailsWith<KmcProtocolException> {
            PacketWriter(nbt = NbtFormat.V1_0_0).writeNamedNbtTag(IntArrayTag(intArrayOf(1)))
        }
        assertEquals(true, failure.message!!.contains("int array"), failure.message!!)
    }

    @Test
    fun `a tag written by an older format is readable by a newer one`() {
        val tag = CompoundTag().addString("text", "KMCProtocol").addInt("value", 7)

        val bytes = PacketWriter(nbt = NbtFormat.V1_0_0).apply { writeNamedNbtTag(tag) }.toByteArray()

        assertEquals(tag, PacketReader(bytes, nbt = NbtFormat.V1_12).readNamedNbtTag())
    }

    private fun roundTrip(tag: CompoundTag, format: NbtFormat): CompoundTag? {
        val bytes = PacketWriter(nbt = format).apply { writeNamedNbtTag(tag) }.toByteArray()
        return PacketReader(bytes, nbt = format).readNamedNbtTag() as CompoundTag?
    }
}
