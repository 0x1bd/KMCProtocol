package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.KMCSerializer
import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element

@Serializable(with = Vec3.Serializer::class)
data class Vec3(val x: Double, val y: Double, val z: Double) {

    object Serializer : KMCSerializer<Vec3>() {

        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor("Position") {
                element<Double>("x")
                element<Double>("y")
                element<Double>("z")
            }

        override fun serialize(encoder: MinecraftPacketEncoder, value: Vec3) {
            encoder.encodeDouble(value.x)
            encoder.encodeDouble(value.y)
            encoder.encodeDouble(value.z)
        }

        override fun deserialize(decoder: MinecraftPacketDecoder): Vec3 {
            return Vec3(
                decoder.decodeDouble(),
                decoder.decodeDouble(),
                decoder.decodeDouble()
            )
        }
    }

}