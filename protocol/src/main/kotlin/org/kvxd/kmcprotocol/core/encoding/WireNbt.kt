package org.kvxd.kmcprotocol.core.encoding

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.NbtType
import net.lenni0451.mcstructs.nbt.exceptions.NbtReadException
import net.lenni0451.mcstructs.nbt.io.NbtReadTracker
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import org.kvxd.kmcprotocol.core.KmcProtocolException
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UTFDataFormatException
import java.util.zip.GZIPInputStream

internal fun emptyNbtCompound(): CompoundTag = CompoundTag()

internal fun sampleNbtCompound(): CompoundTag = CompoundTag().addString("text", "sample")

internal const val NbtEndTypeId: Byte = 0

internal fun PacketWriter.writeNbtTag(tag: NbtTag) = nbtWrite {
    nbt.io.writeUnnamed(asDataOutput(), tag)
}

internal fun PacketWriter.writeNamedNbtTag(tag: NbtTag) = nbtWrite {
    nbt.io.write(asDataOutput(), "", tag)
}

internal fun PacketReader.readNbtTag(): NbtTag? = nbtRead {
    nbt.io.readUnnamed(asDataInput(), readTracker())
}

internal fun PacketReader.readNamedNbtTag(): NbtTag? = nbtRead {
    nbt.io.readNamed(asDataInput(), readTracker())?.tag
}

internal fun PacketReader.readNbtPayload(type: NbtType): NbtTag = nbtRead {
    val tracker = readTracker()
    tracker.pushDepth()
    nbt.io.reader.read(type, asDataInput(), tracker).also { tracker.popDepth() }
}

internal fun PacketWriter.writeNbtString(value: String) = nbtWrite {
    asDataOutput().writeUTF(value)
}

internal fun PacketWriter.writeCompressedNbtTag(tag: NbtTag?) {
    if (tag == null) {
        writeShort(-1)
        return
    }

    val compressed = ByteArrayOutputStream()
    nbtWrite { nbt.io.write(compressed, "", tag, true) }
    val bytes = compressed.toByteArray()

    if (bytes.size > Short.MAX_VALUE) {
        throw KmcProtocolException(
            "Compressed NBT is ${bytes.size} bytes, which exceeds the ${Short.MAX_VALUE} byte " +
                "maximum encodable by its length prefix."
        )
    }

    writeShort(bytes.size.toShort())
    writeBytes(bytes)
}

internal fun PacketReader.readCompressedNbtTag(): NbtTag? {
    val length = readShort().toInt()
    if (length < 0) return null

    val compressed = readBytes(length)
    val payload = compressed.gunzip(limits.maxCompressedNbtLength)
    return PacketReader(payload, limits = limits, nbt = nbt).readNamedNbtTag()
}

private fun ByteArray.gunzip(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit.toLong(), size * 4L).coerceAtLeast(32L).toInt())
    GZIPInputStream(inputStream()).use { input ->
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw KmcProtocolException("Compressed NBT expands beyond the $limit byte limit.")
            }
            output.write(chunk, 0, read)
        }
    }
    return output.toByteArray()
}

private const val NbtBudgetPerRemainingByte = 128L

private fun PacketReader.readTracker(): NbtReadTracker =
    BoundedNbtReadTracker(limits.maxNbtDepth, readBudget())

private fun PacketReader.readBudget(): Long =
    minOf(limits.maxNbtBytes, remaining.toLong() * NbtBudgetPerRemainingByte).coerceAtLeast(1L)

private class BoundedNbtReadTracker(
    private val maxDepth: Int,
    maxBytes: Long,
) : NbtReadTracker(maxDepth, maxBytes) {

    private var depth = 0

    override fun pushDepth() {
        depth++
        if (depth > maxDepth) {
            throw NbtReadException("Tried to read NBT nested deeper than the maximum depth of $maxDepth")
        }
    }

    override fun popDepth() {
        depth--
    }
}

private fun PacketWriter.asDataOutput(): DataOutput = DataOutputStream(PacketWriterStream(this))

private fun PacketReader.asDataInput(): DataInput = DataInputStream(PacketReaderStream(this))

private inline fun <T> nbtWrite(block: () -> T): T =
    try {
        block()
    } catch (cause: UTFDataFormatException) {
        throw KmcProtocolException(
            "NBT string exceeds the 65535 byte maximum encodable by its length prefix: ${cause.message}",
            cause,
        )
    } catch (cause: IOException) {
        throw KmcProtocolException("Failed to write NBT: ${cause.describe()}", cause)
    } catch (cause: RuntimeException) {
        if (cause is KmcProtocolException) throw cause
        throw KmcProtocolException("Failed to write NBT: ${cause.describe()}", cause)
    }

private inline fun <T> nbtRead(block: () -> T): T =
    try {
        block()
    } catch (cause: IOException) {
        throw KmcProtocolException("Failed to read NBT: ${cause.describe()}", cause)
    } catch (cause: RuntimeException) {
        if (cause is KmcProtocolException) throw cause
        throw KmcProtocolException("Failed to read NBT: ${cause.describe()}", cause)
    }

private fun Throwable.describe(): String = message ?: this::class.simpleName.orEmpty()

private class PacketWriterStream(private val writer: PacketWriter) : OutputStream() {
    override fun write(b: Int) = writer.writeByte(b.toByte())

    override fun write(b: ByteArray, off: Int, len: Int) = writer.writeBytes(b, off, len)
}

private class PacketReaderStream(private val reader: PacketReader) : InputStream() {
    override fun read(): Int = if (reader.remaining <= 0) -1 else reader.readByte().toInt() and 0xff

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (reader.remaining <= 0) return -1
        val count = minOf(len, reader.remaining)
        reader.readInto(b, off, count)
        return count
    }

    override fun available(): Int = reader.remaining
}
