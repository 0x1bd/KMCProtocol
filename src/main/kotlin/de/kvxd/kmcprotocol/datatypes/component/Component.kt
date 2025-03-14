package de.kvxd.kmcprotocol.datatypes.component

import de.kvxd.kmcprotocol.serialization.KMCSerializer
import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

object ComponentSerializer : KMCSerializer<Component>() {

    val DEFAULT = GsonComponentSerializer.gson()

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Component", PrimitiveKind.STRING)

    override fun deserialize(decoder: MinecraftPacketDecoder): Component {
        return decoder.decodeComponent()
    }

    override fun serialize(encoder: MinecraftPacketEncoder, value: Component) {
        encoder.encodeComponent(value)
    }
}