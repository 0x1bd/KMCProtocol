package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.KMCSerializer
import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor

const val NAMESPACE_SEPARATOR = ":"
const val DEFAULT_NAMESPACE = "minecraft"

@Serializable(with = Identifier.Serializer::class)
class Identifier private constructor(private val namespace: String, private val path: String) {

    private val combined
        get() = namespace + NAMESPACE_SEPARATOR + path

    companion object {
        fun of(namespace: String, path: String): Identifier =
            Identifier(namespace, path).validate()

        fun of(path: String): Identifier {
            if (path.contains(NAMESPACE_SEPARATOR))
                return fromString(path)

            return of(DEFAULT_NAMESPACE, path).validate()
        }

        fun fromString(string: String): Identifier {
            val namespacePath = string.split(NAMESPACE_SEPARATOR)
            return of(namespacePath[0], namespacePath[1]).validate()
        }
    }

    object Serializer : KMCSerializer<Identifier>() {

        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("Identifier", PrimitiveKind.STRING)

        override fun serialize(encoder: MinecraftPacketEncoder, value: Identifier) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: MinecraftPacketDecoder): Identifier {
            return fromString(decoder.decodeString()).validate()
        }
    }

    override fun toString(): String = combined

    private fun validate(): Identifier {
        if (this.combined.length >= 32767) throw IllegalArgumentException("Identifiers' max length is 32767")
        return this
    }

}