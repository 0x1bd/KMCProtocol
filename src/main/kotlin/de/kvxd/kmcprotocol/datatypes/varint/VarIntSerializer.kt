package de.kvxd.kmcprotocol.datatypes.varint

import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object VarIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("VarInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        val bytes = VarInt.encode(value)
        (encoder as MinecraftPacketEncoder).writeBytes(bytes)
    }

    override fun deserialize(decoder: Decoder): Int {
        return (decoder as MinecraftPacketDecoder).decodeInt()
    }
}