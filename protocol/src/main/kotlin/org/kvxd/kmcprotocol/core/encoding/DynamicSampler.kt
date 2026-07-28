package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kvxd.kmcprotocol.core.KmcProtocolException

internal class DynamicSampler(private val types: ProtocolTypeTable) {

    fun sample(type: JsonElement, context: MutableMap<String, Any?>): DynamicValue {
        types.resolveAlias(type)?.let { return sample(it, context) }

        return when (type) {
            is JsonPrimitive -> DynamicValue(sampleScalar(type.content))
            is JsonArray -> sampleCompound(type, context)
            else -> throw KmcProtocolException("Unsupported protocol type shape: $type")
        }
    }

    fun sampleSwitchPayload(options: JsonObject, context: MutableMap<String, Any?>): DynamicValue {
        val selected = types.selectBranch(options, context) ?: return DynamicValue.obj(emptyMap())
        return DynamicValue.obj(mapOf(switchBranchPropertyName(selected.name) to sample(selected.type, context)))
    }

    private fun sampleCompound(type: JsonArray, context: MutableMap<String, Any?>): DynamicValue {
        types.instantiate(type)?.let { return sample(it, context) }
        val options = type.options()

        return when (val kind = type.kind()) {
            "array" -> DynamicValue.list(
                List(sampleArrayCount(options, context)) { sample(options.elementType(), context) }
            )
            "topBitSetTerminatedArray" -> DynamicValue.list(listOf(sample(options.elementType(), context)))
            "buffer" -> DynamicValue.bytes(ByteArray(options["count"]?.jsonPrimitive?.intOrNull ?: 0))
            "option" -> sample(type.getOrNull(1) ?: VoidType, context)
            "container" -> DynamicValue.obj(sampleContainer(type.fields(), context))
            "switch" -> sampleSwitch(options, context)
            "mapper" -> DynamicValue(
                options["mappings"]?.jsonObject?.get("0")?.jsonPrimitive?.content
                    ?: sample(options.mapperType(), context).value
            )
            "bitflags" -> DynamicValue(options.flagNames().take(1))
            "bitfield" -> DynamicValue.obj(
                type.getOrNull(1)?.jsonArray.orEmpty().associate { it.jsonObject.requiredName() to DynamicValue(0) }
            )
            "pstring" -> DynamicValue("minecraft:sample")
            "registryEntryHolder" -> DynamicValue.obj(mapOf(options.requiredString("baseName") to DynamicValue(1)))
            "registryEntryHolderSet" -> {
                val otherwise = options["otherwise"]?.jsonObject
                val name = otherwise?.get("name")?.jsonPrimitive?.content ?: "ids"
                DynamicValue.obj(
                    mapOf(name to DynamicValue.list(listOf(sample(otherwise?.get("type") ?: VarIntType, context))))
                )
            }
            "entityMetadataLoop" -> DynamicValue.list(emptyList())
            "count" -> DynamicValue(0)
            "void" -> DynamicValue.Null
            else -> throw KmcProtocolException("Unsupported protocol compound type: $kind")
        }
    }

    private fun sampleArrayCount(options: JsonObject, context: Map<String, Any?>): Int {
        val count = options["count"] as? JsonPrimitive ?: return 0
        count.intOrNull?.let { return it }
        return (context[count.content] as? Number)?.toInt() ?: 0
    }

    private fun sampleContainer(
        fields: List<JsonObject>,
        parentContext: MutableMap<String, Any?>,
    ): Map<String, DynamicValue> {
        val values = linkedMapOf<String, DynamicValue>()
        val localContext = parentContext.toMutableMap()
        for (field in fields) {
            val sampled = sample(field["type"] ?: VoidType, localContext)
            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            val name = field["name"]?.jsonPrimitive?.content
            if (anonymous && sampled.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val sampledMap = sampled.value as Map<String, DynamicValue>
                values.putAll(sampledMap)
                localContext.putAll(sampledMap.mapValues { it.value.value })
            } else if (name != null) {
                values[name] = sampled
                localContext[name] = sampled.value
            }
        }
        return values
    }

    private fun sampleSwitch(options: JsonObject, context: MutableMap<String, Any?>): DynamicValue {
        val selected = types.selectBranch(options, context) ?: return DynamicValue.Null
        if (types.switchUsesPayload(options)) {
            return DynamicValue.obj(mapOf(switchBranchPropertyName(selected.name) to sample(selected.type, context)))
        }
        return sample(selected.type, context)
    }

    private fun sampleScalar(name: String): Any? =
        when (name) {
            "void" -> null
            "bool" -> true
            "i8", "u8", "i16", "u16", "i32", "u32", "varint", "optvarint" -> 0
            "i64", "varlong" -> 0L
            "f32" -> 0f
            "f64" -> 0.0
            "string" -> "minecraft:sample"
            "UUID" -> java.util.UUID(0L, 1L)
            "restBuffer" -> ByteArray(0)
            "anonymousNbt" -> EmptyNbtCompound
            "anonOptionalNbt" -> null
            "nbt" -> EmptyNbtCompound
            "compressedNbt" -> null
            "u64" -> 0L
            "byte_array" -> ByteArray(0)
            "lpVec3" -> mapOf("x" to DynamicValue(0.0), "y" to DynamicValue(0.0), "z" to DynamicValue(0.0))
            "optionalNbt" -> null
            else -> throw KmcProtocolException("Unsupported scalar protocol type: $name")
        }
}
