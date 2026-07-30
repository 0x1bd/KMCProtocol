package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.benwoodworth.knbt.NbtTag
import org.kvxd.kmcprotocol.core.KmcProtocolException
import java.util.UUID

internal class DynamicEncoder(
    private val types: ProtocolTypeTable,
    private val writer: PacketWriter,
    private val sampler: DynamicSampler,
) {

    fun encode(type: JsonElement, value: DynamicValue, context: ProtocolScope) {
        types.resolveAlias(type)?.let { return encode(it, value, context) }

        when (type) {
            is JsonPrimitive -> encodeScalar(type.content, value.value)
            is JsonArray -> encodeCompound(type, value, context)
            else -> throw KmcProtocolException("Unsupported protocol type shape: $type")
        }
    }

    fun encodeSwitchPayload(options: JsonObject, value: DynamicValue, context: ProtocolScope) {
        val selected = types.selectBranch(options, context) ?: return
        val branchValue = value.asObject()[switchBranchPropertyName(selected.name)] ?: value
        encode(selected.type, branchValue, context)
    }

    private fun encodeCompound(type: JsonArray, value: DynamicValue, context: ProtocolScope) {
        types.instantiate(type)?.let { return encode(it, value, context) }
        val options = type.options()

        when (val kind = type.kind()) {
            "array" -> {
                val values = value.asList()
                if (options["count"] == null) writeCount(options["countType"], values.size)
                values.forEach { encode(options.elementType(), it, context) }
            }

            "buffer" -> {
                val bytes = value.asBytes()
                if (options["count"] == null) writeCount(options["countType"], bytes.size)
                writer.writeBytes(bytes)
            }

            "option" -> {
                val nested = value.value
                writer.writeByte(if (nested == null) 0 else 1)
                if (nested != null) encode(type.getOrNull(1) ?: VoidType, DynamicValue(nested), context)
            }

            "container" -> encodeContainer(type.fields(), value.asObject(), context)
            "switch" -> encodeSwitchPayload(options, value, context)
            "mapper" -> encode(options.mapperType(), DynamicValue(value.mapperValue(options)), context)
            "bitflags" -> encodeBitFlags(options, value, context)
            "bitfield" -> encodeBitField(type.getOrNull(1)?.jsonArray.orEmpty(), value.asObject())
            "pstring" -> encodeScalar("string", value.value)
            "topBitSetTerminatedArray" -> encodeTopBitSetArray(options.elementType(), value.asList(), context)
            "registryEntryHolder" -> encodeRegistryEntryHolder(options, value.asObject(), context)
            "registryEntryHolderSet" -> encodeRegistryEntryHolderSet(options, value.asObject(), context)
            "entityMetadataLoop" -> encodeEntityMetadataLoop(options, value, context)
            "count" -> encode(options.mapperType(), value, context)
            "void" -> Unit
            else -> throw KmcProtocolException("Unsupported protocol compound type: $kind")
        }
    }

    private fun encodeContainer(
        fields: List<JsonObject>,
        values: Map<String, DynamicValue>,
        parentContext: ProtocolScope,
    ) {
        val localContext = parentContext.child()
        for (field in fields) {
            val fieldType = field["type"] ?: VoidType
            val name = field["name"]?.jsonPrimitive?.content
            val fieldValue = countedSize(fieldType, values)
                ?: if (name != null) {
                    values[name] ?: sampler.sample(fieldType, localContext)
                } else {
                    DynamicValue.obj(values)
                }

            encode(fieldType, fieldValue, localContext)

            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            if (anonymous && fieldValue.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                localContext.putAll((fieldValue.value as Map<String, DynamicValue>).mapValues { it.value.value })
            } else if (name != null) {
                localContext.put(name, fieldValue.value)
            }
        }
    }

    private fun countedSize(fieldType: JsonElement, values: Map<String, DynamicValue>): DynamicValue? {
        val array = fieldType as? JsonArray ?: return null
        if (array.kind() != "count") return null
        val countFor = array.options()["countFor"]?.jsonPrimitive?.content ?: return null
        val counted = values[countFor.substringAfterLast('/')] ?: return DynamicValue(0)
        return DynamicValue(
            when (val raw = counted.value) {
                is Collection<*> -> raw.size
                is ByteArray -> raw.size
                else -> 0
            }
        )
    }

    private fun encodeBitFlags(options: JsonObject, value: DynamicValue, context: ProtocolScope) {
        val flags = options.flagNames()
        val selected = value.asStringList().toSet()
        val mask = flags.foldIndexed(0) { index, acc, flag -> if (flag in selected) acc or (1 shl index) else acc }
        encode(options.mapperType(), DynamicValue(mask), context)
    }

    private fun encodeBitField(fields: List<JsonElement>, values: Map<String, DynamicValue>) {
        val totalBits = fields.sumOf { it.jsonObject.bitSize() }
        var packed = 0L
        var offset = totalBits
        fields.forEach { fieldElement ->
            val field = fieldElement.jsonObject
            val size = field.bitSize()
            offset -= size
            val mask = if (size == 64) -1L else (1L shl size) - 1
            packed = packed or ((values[field.requiredName()].asNumber().toLong() and mask) shl offset)
        }
        writer.writeSizedInteger(packed, totalBits)
    }

    private fun encodeTopBitSetArray(
        elementType: JsonElement,
        values: List<DynamicValue>,
        context: ProtocolScope,
    ) {
        val firstField = (elementType as? JsonArray)?.fields()?.firstOrNull()?.get("name")?.jsonPrimitive?.content
        values.forEachIndexed { index, element ->
            val map = element.asObject().toMutableMap()
            if (firstField != null) {
                val raw = map[firstField].asNumber().toInt()
                map[firstField] = DynamicValue(if (index == values.lastIndex) raw and 0x7f else raw or 0x80)
            }
            encode(elementType, DynamicValue.obj(map), context)
        }
    }

    private fun encodeEntityMetadataLoop(options: JsonObject, value: DynamicValue, context: ProtocolScope) {
        val elementType = options.elementType()
        value.asList().forEach { encode(elementType, it, context) }
        writer.writeByte(options.endValue().toByte())
    }

    private fun encodeRegistryEntryHolder(
        options: JsonObject,
        values: Map<String, DynamicValue>,
        context: ProtocolScope,
    ) {
        val otherwise = options.requiredObject("otherwise")
        val direct = values[otherwise.requiredName()]
        if (direct?.value != null) {
            writer.writeVarInt(0)
            encode(otherwise["type"] ?: VoidType, direct, context)
        } else {
            writer.writeVarInt(values[options.requiredString("baseName")].asNumber().toInt() + 1)
        }
    }

    private fun encodeRegistryEntryHolderSet(
        options: JsonObject,
        values: Map<String, DynamicValue>,
        context: ProtocolScope,
    ) {
        val base = options.requiredObject("base")
        val baseValue = values[base.requiredName()]
        if (baseValue?.value != null) {
            writer.writeVarInt(0)
            encode(base["type"] ?: StringType, baseValue, context)
            return
        }

        val otherwise = options.requiredObject("otherwise")
        val ids = values[otherwise.requiredName()]?.asList().orEmpty()
        writer.writeVarInt(ids.size + 1)
        ids.forEach { encode(otherwise["type"] ?: VarIntType, it, context) }
    }

    private fun encodeScalar(name: String, value: Any?) {
        when (name) {
            "void" -> Unit
            "bool" -> writer.writeByte(if (value == true) 1 else 0)
            "i8" -> writer.writeByte((value as? Number)?.toByte() ?: 0)
            "u8" -> writer.writeByte((value as? Number)?.toInt()?.toByte() ?: 0)
            "i16" -> writer.writeShort((value as? Number)?.toShort() ?: 0)
            "u16" -> writer.writeShort((value as? Number)?.toInt()?.toShort() ?: 0)
            "i32" -> writer.writeInt((value as? Number)?.toInt() ?: 0)
            "u32" -> writer.writeInt((value as? Number)?.toLong()?.toInt() ?: 0)
            "varint", "optvarint" -> writer.writeVarInt((value as? Number)?.toInt() ?: 0)
            "i64" -> writer.writeLong((value as? Number)?.toLong() ?: 0L)
            "varlong" -> writer.writeVarLong((value as? Number)?.toLong() ?: 0L)
            "f32" -> writer.writeFloat((value as? Number)?.toFloat() ?: 0f)
            "f64" -> writer.writeDouble((value as? Number)?.toDouble() ?: 0.0)
            "string" -> writer.writeString(value as? String ?: "")
            "UUID" -> writer.writeUuid(value.asUuid())
            "restBuffer" -> writer.writeBytes((value as? ByteArray) ?: ByteArray(0))
            "anonymousNbt" -> writer.writeNbtTag((value as? NbtTag) ?: EmptyNbtCompound)
            "anonOptionalNbt" -> {
                val tag = value as? NbtTag
                if (tag == null) writer.writeByte(NbtType.End.toByte()) else writer.writeNbtTag(tag)
            }
            "nbt" -> writer.writeNamedNbtTag((value as? NbtTag) ?: EmptyNbtCompound)
            "compressedNbt" -> writer.writeCompressedNbtTag(value as? NbtTag)
            "u64" -> writer.writeLong((value as? Number)?.toLong() ?: 0L)
            "byte_array" -> {
                val bytes = (value as? ByteArray) ?: ByteArray(0)
                writer.writeShort(bytes.size.toShort())
                writer.writeBytes(bytes)
            }
            "lpVec3" -> {
                val components = DynamicValue(value).asObject()
                writer.writeLpVec3(
                    components["x"].asNumber().toDouble(),
                    components["y"].asNumber().toDouble(),
                    components["z"].asNumber().toDouble(),
                )
            }
            "optionalNbt" -> {
                val tag = value as? NbtTag
                if (tag == null) writer.writeByte(NbtType.End.toByte()) else writer.writeNamedNbtTag(tag)
            }
            else -> throw KmcProtocolException("Unsupported scalar protocol type: $name")
        }
    }

    private fun writeCount(countType: JsonElement?, count: Int) {
        when ((countType as? JsonPrimitive)?.content ?: "varint") {
            "u8", "i8" -> writer.writeByte(count.toByte())
            "u16", "i16" -> writer.writeShort(count.toShort())
            "i32" -> writer.writeInt(count)
            else -> writer.writeVarInt(count)
        }
    }

    private fun DynamicValue.mapperValue(options: JsonObject): Any? {
        val value = value
        if (value !is String) return value
        return options["mappings"]?.jsonObject.orEmpty()
            .entries
            .firstOrNull { it.value.jsonPrimitive.content == value }
            ?.key
            ?.toIntOrNull()
            ?: 0
    }

    private fun Any?.asUuid(): UUID =
        when (this) {
            is UUID -> this
            is String -> runCatching { UUID.fromString(this) }.getOrDefault(NilUuid)
            else -> NilUuid
        }

    private companion object {
        val NilUuid: UUID = UUID(0L, 0L)
    }
}

internal val VoidType = JsonPrimitive("void")
internal val VarIntType = JsonPrimitive("varint")
internal val StringType = JsonPrimitive("string")

internal fun JsonObject.elementType(): JsonElement = this["type"] ?: VoidType

internal fun JsonObject.mapperType(): JsonElement = this["type"] ?: VarIntType

internal fun JsonObject.flagNames(): List<String> =
    this["flags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }

internal fun JsonObject.endValue(): Int = this["endVal"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0xff

internal fun JsonObject.bitSize(): Int = this["size"]?.jsonPrimitive?.int
    ?: throw KmcProtocolException("Bitfield entry is missing a size: $this")

internal fun JsonObject.requiredName(): String = requiredString("name")

internal fun JsonObject.requiredString(key: String): String =
    this[key]?.jsonPrimitive?.content
        ?: throw KmcProtocolException("Protocol type is missing required '$key' entry: $this")

internal fun JsonObject.requiredObject(key: String): JsonObject =
    this[key]?.jsonObject
        ?: throw KmcProtocolException("Protocol type is missing required '$key' object: $this")
