package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

internal val jsonSerializer = GsonComponentSerializer.gson()

object JsonTextCodec : ElementCodec<Component> {

    override suspend fun encode(channel: ByteWriteChannel, value: Component) {
        StringCodec.encode(channel, jsonSerializer.serialize(value))
    }

    override suspend fun decode(channel: ByteReadChannel): Component = try {
        jsonSerializer.deserialize(StringCodec.decode(channel))
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to decode JSON Text Component: ${e.message}")
    }
}