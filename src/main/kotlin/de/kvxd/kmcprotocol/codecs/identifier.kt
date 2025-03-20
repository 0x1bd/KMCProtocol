package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import kotlin.reflect.KProperty1

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
            return of(namespacePath[0], namespacePath[1]).validate()
        }
    }

    override fun toString(): String = combined

    private fun validate(): Identifier {
        if (this.combined.length >= 32767) throw IllegalArgumentException("Identifiers' max length is 32767")
        return this
    }

}

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.identifier(
    property: KProperty1<T, Identifier>
) {
    addCodec<Identifier>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeString(value.toString())
        },
        decoder = { channel -> Identifier.of(channel.readString()) }
    )
}