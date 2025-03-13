package de.kvxd.kmcprotocol.datatypes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val NAMESPACE_SEPARATOR = ":"
const val DEFAULT_NAMESPACE = "minecraft"

@Serializable(with = Identifier.Serializer::class)
class Identifier private constructor(private val namespace: String, private val path: String) {

    companion object {

        fun of(namespace: String, path: String): Identifier =
            Identifier(namespace, path)

        fun of(path: String): Identifier =
            Identifier(DEFAULT_NAMESPACE, path)

        fun fromString(string: String): Identifier {
            val namespacePath = string.split(NAMESPACE_SEPARATOR)
            return of(namespacePath[0], namespacePath[1])
        }
    }

    object Serializer : KSerializer<Identifier> {

        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("Identifier", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Identifier) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Identifier =
            fromString(decoder.decodeString())
    }

    override fun toString(): String =
        (namespace + NAMESPACE_SEPARATOR + path).apply {
            if (this.length >= 32767) throw IllegalArgumentException("Identifiers' max length is 32767")
        }

}