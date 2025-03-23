package de.kvxd.kmcprotocol.codec.codecs

import com.google.gson.Gson
import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.reflect.KClass

//jsonSerializer is used in case we need to serialize a Component

class JsonStringCodec<T : Any>(private val kClass: KClass<T>, private val gson: Gson) : ElementCodec<T> {

    override suspend fun encode(channel: ByteWriteChannel, value: T) {
        val json = gson.toJson(value)
        StringCodec.encode(channel, json)
    }

    override suspend fun decode(channel: ByteReadChannel): T {
        return gson.fromJson(StringCodec.decode(channel), kClass.java)
    }
}

inline fun <reified T : Any> jsonStringCodec(
    gson: Gson = GsonComponentSerializer.gson().serializer()
): JsonStringCodec<T> {
    return JsonStringCodec(T::class, gson)
}