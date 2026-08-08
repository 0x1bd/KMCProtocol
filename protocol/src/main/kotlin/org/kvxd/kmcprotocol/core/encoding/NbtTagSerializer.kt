package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.kmcprotocol.core.KmcProtocolException

internal const val NbtSerialNamePrefix = "net.lenni0451.mcstructs.nbt."

object NbtTagSerializer : KSerializer<NbtTag> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("${NbtSerialNamePrefix}NbtTag", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NbtTag) = unsupported()

    override fun deserialize(decoder: Decoder): NbtTag = unsupported()

    private fun unsupported(): Nothing = throw KmcProtocolException(
        "NBT tags are only encodable through the Minecraft packet codec. Annotate the property with " +
            "ProtocolBinary or ProtocolType instead of serializing it with a generic format."
    )
}

internal fun SerialDescriptor.isNbt(): Boolean = serialName.startsWith(NbtSerialNamePrefix)
