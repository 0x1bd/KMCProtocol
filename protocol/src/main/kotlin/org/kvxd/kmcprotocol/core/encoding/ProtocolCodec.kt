package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import net.benwoodworth.knbt.NbtTag

class ProtocolCodec(val types: ProtocolTypeTable) {

    private val sampler = DynamicSampler(types)

    fun parse(json: String): JsonElement = types.parse(json)

    internal fun encodeAny(
        type: JsonElement,
        value: Any?,
        writer: PacketWriter,
        context: MutableMap<String, Any?>,
    ) {
        DynamicEncoder(types, writer, sampler).encode(type, value.toDynamicValue(), ProtocolScope.root(context))
    }

    internal fun <T> decodeAny(
        type: JsonElement,
        deserializer: DeserializationStrategy<T>,
        reader: PacketReader,
        context: MutableMap<String, Any?>,
    ): T {
        val decoder = DynamicDecoder(types, reader)
        val scope = ProtocolScope.root(context)
        val value = if (type.usesSwitchPayload(deserializer)) {
            decoder.decodeSwitchPayload(type.jsonArray.options(), scope)
        } else {
            decoder.decode(type, scope)
        }
        return value.materialize(deserializer)
    }

    internal fun <T> sampleAny(
        type: JsonElement,
        deserializer: DeserializationStrategy<T>,
        context: MutableMap<String, Any?>,
    ): T {
        val scope = ProtocolScope.root(context)
        val value = if (type.usesSwitchPayload(deserializer)) {
            sampler.sampleSwitchPayload(type.jsonArray.options(), scope)
        } else {
            sampler.sample(type, scope)
        }
        return value.materialize(deserializer)
    }

    internal fun sampleText(type: JsonElement, context: MutableMap<String, Any?>): String =
        sampler.sample(type, ProtocolScope.root(context)).value?.toString() ?: ""

    internal fun decodeText(type: JsonElement, reader: PacketReader, context: MutableMap<String, Any?>): String =
        DynamicDecoder(types, reader).decode(type, ProtocolScope.root(context)).value?.toString() ?: ""

    @Suppress("UNCHECKED_CAST")
    private fun <T> DynamicValue.materialize(deserializer: DeserializationStrategy<T>): T {
        val descriptor = deserializer.descriptor
        if (value == null && (descriptor.isNullable || descriptor.isNbt())) return null as T
        if (value is ByteArray && descriptor.serialName == "kotlin.ByteArray") return value as T
        if (value is NbtTag && descriptor.isNbt()) return value as T
        if (value is java.util.UUID && descriptor.serialName == "java.util.UUID") return value as T
        return deserializer.deserialize(DynamicValueBackedDecoder(value))
    }

    private fun JsonElement.usesSwitchPayload(deserializer: DeserializationStrategy<*>): Boolean =
        isSwitch() && deserializer.descriptor.annotations.any { it is ProtocolSwitchPayload }

    companion object {
        val Empty = ProtocolCodec(ProtocolTypeTable.Empty)

        fun fromResource(resourcePath: String, classLoader: ClassLoader? = null): ProtocolCodec =
            ProtocolCodec(ProtocolTypeTable.fromResource(resourcePath, classLoader))

        fun fromProtocolJson(protocol: JsonObject): ProtocolCodec =
            ProtocolCodec(ProtocolTypeTable.fromProtocolJson(protocol))
    }
}

private fun kotlinx.serialization.descriptors.SerialDescriptor.isNbt(): Boolean =
    serialName.startsWith("net.benwoodworth.knbt.")
