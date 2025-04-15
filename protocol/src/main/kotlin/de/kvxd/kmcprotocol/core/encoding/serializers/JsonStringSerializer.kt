package de.kvxd.kmcprotocol.core.encoding.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.reflect.KClass

internal val gson = GsonComponentSerializer.gson().serializer()

class JsonStringSerializer<T : Any>(private val kClass: KClass<T>) : KSerializer<T> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("jsonString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        val json = gson.toJson(value)
        encoder.encodeString(json)
    }

    override fun deserialize(decoder: Decoder): T {
        val json = decoder.decodeString()
        return gson.fromJson(json, kClass.java)
    }

}

inline fun <reified T : Any> jsonString() = JsonStringSerializer(T::class)