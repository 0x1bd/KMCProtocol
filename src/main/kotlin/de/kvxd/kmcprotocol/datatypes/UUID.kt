package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.KMCSerializer
import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import java.util.*

object UuidSerializer : KMCSerializer<UUID>() {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

    override fun deserialize(decoder: MinecraftPacketDecoder): UUID {
        return decoder.decodeUUID()
    }

    override fun serialize(encoder: MinecraftPacketEncoder, value: UUID) {
        return encoder.encodeUUID(value)
    }

}