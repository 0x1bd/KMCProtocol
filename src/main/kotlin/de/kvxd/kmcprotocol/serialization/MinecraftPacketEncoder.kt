package de.kvxd.kmcprotocol.serialization

import de.kvxd.kmcprotocol.datatypes.VarInt
import de.kvxd.kmcprotocol.datatypes.VarLong
import de.kvxd.kmcprotocol.datatypes.component.ComponentSerializer
import de.kvxd.kmcprotocol.datatypes.component.NbtComponentSerializer
import io.ktor.utils.io.core.*
import kotlinx.io.readByteArray
import kotlinx.io.writeDouble
import kotlinx.io.writeFloat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.SerializersModule
import net.kyori.adventure.text.Component
import org.cloudburstmc.nbt.NBTOutputStream
import org.cloudburstmc.nbt.NbtType
import java.util.*
import kotlin.text.toByteArray


@OptIn(ExperimentalSerializationApi::class)
class MinecraftPacketEncoder : AbstractEncoder() {

    private val builder = BytePacketBuilder()

    override val serializersModule: SerializersModule = PacketSerializer.serializersModule

    fun writeBytes(bytes: ByteArray) {
        builder.writeFully(bytes)
    }

    fun getBytes(): ByteArray {
        return builder.build().readByteArray()
    }

    override fun encodeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        builder.writeFully(VarInt.encode(bytes.size))
        builder.writeFully(bytes)
    }

    override fun encodeShort(value: Short) {
       builder.writeShort(value)
    }

    override fun encodeInt(value: Int) {
        builder.writeInt(value)
    }

    fun encodeVarInt(varInt: Int) {
        writeBytes(VarInt.encode(varInt))
    }

    fun encodeVarLong(varLong: Long) {
        writeBytes(VarLong.encode(varLong))
    }

    fun encodeUUID(uuid: UUID) {
        encodeLong(uuid.mostSignificantBits)
        encodeLong(uuid.leastSignificantBits)
    }

    fun encodeTag(tag: Any?) {
        val output = SourceDataOutput(builder)

        if (tag == null) {
            output.writeByte(0)
            return
        }

        val type = NbtType.byClass(tag::class.java)
        output.writeByte(type.id)

        NBTOutputStream(SourceDataOutput(builder)).writeValue(tag, 512)
    }

    fun encodeComponent(component: Component) {
        val json = ComponentSerializer.DEFAULT.serializeToTree(component)
        val tag = NbtComponentSerializer.jsonComponentToTag(json)

        encodeTag(tag)
    }

    override fun encodeByte(value: Byte) {
        builder.writeByte(value)
    }

    override fun encodeBoolean(value: Boolean) {
        if (value)
            builder.writeByte(0x01)
        else
            builder.writeByte(0x00)
    }

    override fun encodeLong(value: Long) {
        builder.writeLong(value)
    }

    override fun encodeFloat(value: Float) {
        builder.writeFloat(value)
    }

    override fun encodeDouble(value: Double) {
        builder.writeDouble(value)
    }
}