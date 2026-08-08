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

internal class DynamicDecoder(
    private val types: ProtocolTypeTable,
    private val reader: PacketReader,
) {

    fun decode(type: JsonElement, context: ProtocolScope): DynamicValue {
        types.resolveAlias(type)?.let { return decode(it, context) }

        return when (type) {
            is JsonPrimitive -> DynamicValue(decodeScalar(type.content))
            is JsonArray -> decodeCompound(type, context)
            else -> throw KmcProtocolException("Unsupported protocol type shape: $type")
        }
    }

    fun decodeSwitchPayload(options: JsonObject, context: ProtocolScope): DynamicValue {
        val selected = types.selectBranch(options, context) ?: return DynamicValue.obj(emptyMap())
        return DynamicValue.obj(mapOf(switchBranchPropertyName(selected.name) to decode(selected.type, context)))
    }

    private fun decodeCompound(type: JsonArray, context: ProtocolScope): DynamicValue {
        types.instantiate(type)?.let { return decode(it, context) }
        val options = type.options()

        return when (val kind = type.kind()) {
            "array" -> {
                val count = readArrayCount(options, context)
                DynamicValue.list(List(count) { decode(options.elementType(), context) })
            }

            "buffer" -> DynamicValue.bytes(reader.readBytes(readArrayCount(options, context)))

            "option" ->
                if (reader.readByte().toInt() != 0) decode(type.getOrNull(1) ?: VoidType, context)
                else DynamicValue.Null

            "container" -> DynamicValue.obj(decodeContainer(type.fields(), context))
            "switch" -> decodeSwitch(options, context)
            "mapper" -> DynamicValue(decodeMapper(options, context))
            "bitflags" -> decodeBitFlags(options, context)
            "bitfield" -> DynamicValue.obj(decodeBitField(type.getOrNull(1)?.jsonArray.orEmpty()))
            "pstring" -> DynamicValue(decodeScalar("string"))
            "topBitSetTerminatedArray" -> decodeTopBitSetArray(options.elementType(), context)
            "registryEntryHolder" -> DynamicValue.obj(decodeRegistryEntryHolder(options, context))
            "registryEntryHolderSet" -> DynamicValue.obj(decodeRegistryEntryHolderSet(options, context))
            "entityMetadataLoop" -> decodeEntityMetadataLoop(options, context)
            "count" -> decode(options.mapperType(), context)
            "void" -> DynamicValue.Null
            else -> throw KmcProtocolException("Unsupported protocol compound type: $kind")
        }
    }

    private fun decodeContainer(
        fields: List<JsonObject>,
        parentContext: ProtocolScope,
    ): Map<String, DynamicValue> {
        val values = linkedMapOf<String, DynamicValue>()
        val localContext = parentContext.child()
        for (field in fields) {
            val decoded = decode(field["type"] ?: VoidType, localContext)
            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            val name = field["name"]?.jsonPrimitive?.content
            if (anonymous && decoded.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val decodedMap = decoded.value as Map<String, DynamicValue>
                values.putAll(decodedMap)
                localContext.putAll(decodedMap.mapValues { it.value.value })
            } else if (name != null) {
                values[name] = decoded
                localContext.put(name, decoded.value)
            }
        }
        return values
    }

    private fun decodeSwitch(options: JsonObject, context: ProtocolScope): DynamicValue {
        val selected = types.selectBranch(options, context) ?: return DynamicValue.Null
        if (types.switchUsesPayload(options)) {
            return DynamicValue.obj(mapOf(switchBranchPropertyName(selected.name) to decode(selected.type, context)))
        }
        return decode(selected.type, context)
    }

    private fun decodeMapper(options: JsonObject, context: ProtocolScope): Any? {
        val raw = decode(options.mapperType(), context).value as Number
        return options["mappings"]?.jsonObject?.get(raw.toString())?.jsonPrimitive?.content ?: raw
    }

    private fun decodeBitFlags(options: JsonObject, context: ProtocolScope): DynamicValue {
        val mask = (decode(options.mapperType(), context).value as Number).toInt()
        return DynamicValue(options.flagNames().filterIndexed { index, _ -> mask and (1 shl index) != 0 })
    }

    private fun decodeBitField(fields: List<JsonElement>): Map<String, DynamicValue> {
        val totalBits = fields.sumOf { it.jsonObject.bitSize() }
        val packed = reader.readSizedInteger(totalBits)
        var offset = totalBits
        return fields.associate { fieldElement ->
            val field = fieldElement.jsonObject
            val size = field.bitSize()
            offset -= size
            val mask = if (size == 64) -1L else (1L shl size) - 1
            val raw = (packed ushr offset) and mask
            val signed = field["signed"]?.jsonPrimitive?.booleanOrNull == true
            val value = if (signed && size < 64 && raw and (1L shl (size - 1)) != 0L) raw or (-1L shl size) else raw
            field.requiredName() to DynamicValue(value)
        }
    }

    private fun decodeTopBitSetArray(elementType: JsonElement, context: ProtocolScope): DynamicValue {
        val declared = types.containerFieldsOf(elementType)
            ?: throw KmcProtocolException("topBitSetTerminatedArray element type is not a container: $elementType")
        val fields = types.flattenAnonymousFields(declared)
        val firstField = fields.firstOrNull()
            ?: throw KmcProtocolException("topBitSetTerminatedArray element container has no fields.")
        if (firstField["type"]?.jsonPrimitive?.content !in setOf("i8", "u8")) {
            throw KmcProtocolException(
                "topBitSetTerminatedArray requires a leading i8/u8 field but found ${firstField["type"]}."
            )
        }

        val firstName = firstField.requiredName()
        val values = mutableListOf<DynamicValue>()
        do {
            val firstByte = reader.readByte().toInt()
            val hasMore = firstByte and 0x80 != 0
            values += DynamicValue.obj(decodeLoopEntry(fields, firstName, firstByte and 0x7f, context))
        } while (hasMore)

        return DynamicValue.list(values)
    }

    private fun decodeEntityMetadataLoop(options: JsonObject, context: ProtocolScope): DynamicValue {
        val elementType = options.elementType()
        val endValue = options.endValue()

        val values = mutableListOf<DynamicValue>()
        while (true) {
            if (reader.peekUnsignedByte() == endValue) {
                reader.readByte()
                break
            }
            values += decode(elementType, context.child())
        }
        return DynamicValue.list(values)
    }

    private fun decodeLoopEntry(
        fields: List<JsonObject>,
        firstName: String,
        firstValue: Int,
        context: ProtocolScope,
    ): Map<String, DynamicValue> {
        val localContext = context.child()
        val values = linkedMapOf(firstName to DynamicValue(firstValue))
        localContext.put(firstName, firstValue)
        fields.drop(1).forEach { field ->
            val name = field["name"]?.jsonPrimitive?.content ?: return@forEach
            val decoded = decode(field["type"] ?: VoidType, localContext)
            values[name] = decoded
            localContext.put(name, decoded.value)
        }
        return values
    }

    private fun decodeRegistryEntryHolder(
        options: JsonObject,
        context: ProtocolScope,
    ): Map<String, DynamicValue> {
        val rawId = reader.readVarInt()
        if (rawId != 0) return linkedMapOf(options.requiredString("baseName") to DynamicValue(rawId - 1))

        val otherwise = options.requiredObject("otherwise")
        return linkedMapOf(otherwise.requiredName() to decode(otherwise["type"] ?: VoidType, context))
    }

    private fun decodeRegistryEntryHolderSet(
        options: JsonObject,
        context: ProtocolScope,
    ): Map<String, DynamicValue> {
        val tagOrCount = reader.readVarInt()
        if (tagOrCount == 0) {
            val base = options.requiredObject("base")
            return linkedMapOf(base.requiredName() to decode(base["type"] ?: StringType, context))
        }

        val otherwise = options.requiredObject("otherwise")
        val elementType = otherwise["type"] ?: VarIntType
        val count = reader.requireElementCount(tagOrCount - 1, 1, "Registry entry holder set")
        return linkedMapOf(
            otherwise.requiredName() to DynamicValue.list(List(count) { decode(elementType, context) })
        )
    }

    private fun decodeScalar(name: String): Any? =
        when (name) {
            "void" -> null
            "bool" -> reader.readByte().toInt() != 0
            "i8" -> reader.readByte()
            "u8" -> reader.readByte().toUByte().toShort()
            "i16" -> reader.readShort()
            "u16" -> reader.readShort().toUShort().toInt()
            "i32" -> reader.readInt()
            "u32" -> reader.readInt().toUInt().toLong()
            "varint", "optvarint" -> reader.readVarInt()
            "i64" -> reader.readLong()
            "varlong" -> reader.readVarLong()
            "f32" -> reader.readFloat()
            "f64" -> reader.readDouble()
            "string" -> reader.readString()
            "UUID" -> reader.readUuid()
            "restBuffer" -> reader.readRemaining()
            "anonymousNbt" -> reader.readNbtTag() ?: emptyNbtCompound()
            "anonOptionalNbt" -> reader.readNbtTag()
            "nbt" -> reader.readNamedNbtTag() ?: emptyNbtCompound()
            "compressedNbt" -> reader.readCompressedNbtTag()
            "u64" -> reader.readLong()
            "byte_array" -> reader.readBytes(reader.readShort().toInt())
            "lpVec3" -> reader.readLpVec3().let { (x, y, z) ->
                mapOf("x" to DynamicValue(x), "y" to DynamicValue(y), "z" to DynamicValue(z))
            }
            "optionalNbt" -> reader.readNamedNbtTag()
            else -> throw KmcProtocolException("Unsupported scalar protocol type: $name")
        }

    private fun readArrayCount(options: JsonObject, context: ProtocolScope): Int {
        val count = options["count"]
        if (count is JsonPrimitive) {
            count.intOrNull?.let { return reader.requireElementCount(it, 1, "Array") }
            val referenced = context.resolve(count.content)
            if (referenced != null) {
                val resolved = (referenced as? Number)?.toInt()
                    ?: throw KmcProtocolException("Array count field '${count.content}' is not numeric: $referenced")
                return reader.requireElementCount(resolved, 1, "Array")
            }
        }
        return reader.requireElementCount(readCount(options["countType"]), 1, "Array")
    }

    private fun readCount(countType: JsonElement?): Int =
        when ((countType as? JsonPrimitive)?.content ?: "varint") {
            "u8" -> reader.readByte().toUByte().toInt()
            "i8" -> reader.readByte().toInt()
            "u16" -> reader.readShort().toUShort().toInt()
            "i16" -> reader.readShort().toInt()
            "i32" -> reader.readInt()
            else -> reader.readVarInt()
        }
}
