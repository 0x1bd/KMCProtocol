package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

const val NAMESPACE_SEPARATOR = ":"
const val DEFAULT_NAMESPACE = "minecraft"

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

            return if (namespacePath.size == 1)
                of(DEFAULT_NAMESPACE, string)
            else
                of(namespacePath[0], namespacePath[1]).validate()
        }
    }

    override fun toString(): String = combined

    private fun validate(): Identifier {
        if (this.combined.length >= 32767) throw IllegalArgumentException("Identifiers' max length is 32767")
        return this
    }

}

object IdentifierCodec : ElementCodec<Identifier> {

    override suspend fun encode(channel: ByteWriteChannel, value: Identifier) {
        StringCodec.encode(channel, value.toString())
    }

    override suspend fun decode(channel: ByteReadChannel): Identifier {
        return Identifier.of(StringCodec.decode(channel))
    }
}