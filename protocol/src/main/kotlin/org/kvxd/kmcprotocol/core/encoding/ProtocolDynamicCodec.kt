package org.kvxd.kmcprotocol.core.encoding

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readDouble
import io.ktor.utils.io.readFloat
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readLong
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readShort
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeDouble
import io.ktor.utils.io.writeFloat
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeLong
import io.ktor.utils.io.writeShort
import kotlinx.io.readByteArray
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtByteArray
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtDouble
import net.benwoodworth.knbt.NbtFloat
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtIntArray
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtLongArray
import net.benwoodworth.knbt.NbtShort
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.reflect.full.memberProperties

/**
 * Encoder, decoder, and sampler for dynamic PrismarineJS protocol type definitions.
 */
object ProtocolDynamicCodec {
    /**
     * Process-wide fallback resource path for protocol.json alias resolution.
     */
    @Volatile
    var protocolResourcePath: String = ""

    /**
     * Parses a protocol type JSON string into a JSON element.
     */
    fun parse(type: String): JsonElement = Json.parseToJsonElement(type)

    /**
     * Encodes a Kotlin value using a dynamic protocol type.
     */
    suspend fun encodeAny(
        type: JsonElement,
        value: Any?,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
        protocolResourcePath: String? = null,
    ) {
        encode(type, value.toProtocolValue(), channel, context, protocolResourcePath)
    }

    /**
     * Decodes a Kotlin value using a dynamic protocol type and deserialization strategy.
     */
    suspend fun <T> decodeAny(
        type: JsonElement,
        deserializer: DeserializationStrategy<T>,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
        protocolResourcePath: String? = null,
    ): T {
        val value = decode(type, channel, context, protocolResourcePath)
        if (deserializer.descriptor.serialName.endsWith("ProtocolValue")) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        if (
            value.value == null &&
            (deserializer.descriptor.isNullable || deserializer.descriptor.serialName.startsWith("net.benwoodworth.knbt."))
        ) {
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
        if (value.value is ByteArray && deserializer.descriptor.serialName == "kotlin.ByteArray") {
            @Suppress("UNCHECKED_CAST")
            return value.value as T
        }
        value.value.directNbtValue<T>(deserializer)?.let { return it }
        return deserializer.deserialize(ProtocolValueBackedDecoder(value.value))
    }

    /**
     * Creates a representative Kotlin value for a dynamic protocol type.
     */
    fun <T> sampleAny(
        type: JsonElement,
        deserializer: DeserializationStrategy<T>,
        context: MutableMap<String, Any?> = mutableMapOf(),
        protocolResourcePath: String? = null,
    ): T {
        val value = sample(type, context, protocolResourcePath)
        if (deserializer.descriptor.serialName.endsWith("ProtocolValue")) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        if (
            value.value == null &&
            (deserializer.descriptor.isNullable || deserializer.descriptor.serialName.startsWith("net.benwoodworth.knbt."))
        ) {
            @Suppress("UNCHECKED_CAST")
            return null as T
        }
        value.value.directNbtValue<T>(deserializer)?.let { return it }
        return deserializer.deserialize(ProtocolValueBackedDecoder(value.value))
    }

    /**
     * Encodes a ProtocolValue using a dynamic protocol type.
     */
    suspend fun encode(
        type: JsonElement,
        value: ProtocolValue,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
        protocolResourcePath: String? = null,
    ) {
        protocolResourcePath?.let { context[ProtocolResourcePathContextKey] = it }
        resolveAlias(type, context)?.let {
            encode(it, value, channel, context)
            return
        }

        when (type) {
            is JsonPrimitive -> encodeScalar(type.content, value.value, channel)
            is JsonArray -> encodeCompound(type, value, channel, context)
            else -> error("Unsupported protocol type shape: $type")
        }
    }

    /**
     * Decodes a ProtocolValue using a dynamic protocol type.
     */
    suspend fun decode(
        type: JsonElement,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
        protocolResourcePath: String? = null,
    ): ProtocolValue {
        protocolResourcePath?.let { context[ProtocolResourcePathContextKey] = it }
        return resolveAlias(type, context)?.let { decode(it, channel, context) } ?:
        when (type) {
            is JsonPrimitive -> ProtocolValue(decodeScalar(type.content, channel))
            is JsonArray -> decodeCompound(type, channel, context)
            else -> error("Unsupported protocol type shape: $type")
        }
    }

    /**
     * Creates a representative ProtocolValue for a dynamic protocol type.
     */
    fun sample(
        type: JsonElement,
        context: MutableMap<String, Any?> = mutableMapOf(),
        protocolResourcePath: String? = null,
    ): ProtocolValue {
        protocolResourcePath?.let { context[ProtocolResourcePathContextKey] = it }
        return resolveAlias(type, context)?.let { sample(it, context) } ?:
        when (type) {
            is JsonPrimitive -> sampleScalar(type.content)
            is JsonArray -> sampleCompound(type, context)
            else -> ProtocolValue()
        }
    }

    private suspend fun encodeCompound(
        type: JsonArray,
        value: ProtocolValue,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val kind = type.kind()
        val options = type.options()

        when (kind) {
            "array" -> {
                val values = value.asList()
                val elementType = options["type"] ?: JsonPrimitive("void")
                val count = options["count"]
                if (count == null || count is JsonPrimitive && count.intOrNull != null) {
                    val countValue = count?.jsonPrimitive?.intOrNull ?: values.size
                    if (count == null) writeCount(options["countType"], countValue, channel)
                }
                values.forEach { encode(elementType, it, channel, context) }
            }

            "buffer" -> {
                val bytes = value.asBytes()
                val count = options["count"]
                if (count == null || count is JsonPrimitive && count.intOrNull != null) {
                    val countValue = count?.jsonPrimitive?.intOrNull ?: bytes.size
                    if (count == null) writeCount(options["countType"], countValue, channel)
                }
                channel.writeFully(bytes)
            }

            "option" -> {
                val nestedType = type.getOrNull(1) ?: JsonPrimitive("void")
                val nested = value.value
                channel.writeByte(if (nested == null) 0 else 1)
                if (nested != null) encode(nestedType, ProtocolValue(nested), channel, context)
            }

            "container" -> encodeContainer(type.fields(), value.asObject(), channel, context)
            "switch" -> encodeSwitch(options, value, channel, context)
            "mapper" -> encode(options["type"] ?: JsonPrimitive("varint"), ProtocolValue(value.mapperValue(options)), channel, context)
            "bitflags" -> encodeBitFlags(options, value, channel, context)
            "bitfield" -> encodeBitField(type.getOrNull(1)?.jsonArray.orEmpty(), value.asObject(), channel)
            "pstring" -> encodeScalar("string", value.value, channel)
            "topBitSetTerminatedArray" -> encodeTopBitSetArray(options["type"] ?: JsonPrimitive("void"), value.asList(), channel, context)
            "registryEntryHolder" -> encodeRegistryEntryHolder(options, value.asObject(), channel, context)
            "registryEntryHolderSet" -> encodeRegistryEntryHolderSet(options, value.asObject(), channel, context)
            "entityMetadataLoop" -> channel.writeByte(0xff.toByte())
            "void" -> Unit
            else -> error("Unsupported protocol compound type: $kind")
        }
    }

    private suspend fun decodeCompound(
        type: JsonArray,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): ProtocolValue {
        val kind = type.kind()
        val options = type.options()

        return when (kind) {
            "array" -> {
                val count = readArrayCount(options, context, channel)
                val elementType = options["type"] ?: JsonPrimitive("void")
                ProtocolValue.list(List(count) { decode(elementType, channel, context) })
            }

            "buffer" -> {
                val count = readArrayCount(options, context, channel)
                val bytes = ByteArray(count)
                channel.readFully(bytes, 0, count)
                ProtocolValue.bytes(bytes)
            }

            "option" -> {
                val present = channel.readByte().toInt() != 0
                if (present) decode(type.getOrNull(1) ?: JsonPrimitive("void"), channel, context) else ProtocolValue(null)
            }

            "container" -> ProtocolValue.obj(decodeContainer(type.fields(), channel, context))
            "switch" -> decodeSwitch(options, channel, context)
            "mapper" -> ProtocolValue(decodeMapper(options, channel, context))
            "bitflags" -> decodeBitFlags(options, channel, context)
            "bitfield" -> ProtocolValue.obj(decodeBitField(type.getOrNull(1)?.jsonArray.orEmpty(), channel))
            "pstring" -> ProtocolValue(decodeScalar("string", channel))
            "topBitSetTerminatedArray" -> decodeTopBitSetArray(options["type"] ?: JsonPrimitive("void"), channel, context)
            "registryEntryHolder" -> ProtocolValue.obj(decodeRegistryEntryHolder(options, channel, context))
            "registryEntryHolderSet" -> ProtocolValue.obj(decodeRegistryEntryHolderSet(options, channel, context))
            "entityMetadataLoop" -> {
                val bytes = mutableListOf<Byte>()
                do {
                    val byte = channel.readByte()
                    bytes += byte
                } while ((byte.toInt() and 0xff) != 0xff)
                ProtocolValue.bytes(bytes.toByteArray())
            }
            "void" -> ProtocolValue(null)
            else -> error("Unsupported protocol compound type: $kind")
        }
    }

    private fun sampleCompound(type: JsonArray, context: MutableMap<String, Any?>): ProtocolValue {
        val kind = type.kind()
        val options = type.options()
        return when (kind) {
            "array" -> ProtocolValue.list(emptyList())
            "topBitSetTerminatedArray" -> ProtocolValue.list(listOf(sample(options["type"] ?: JsonPrimitive("void"), context)))
            "buffer" -> ProtocolValue.bytes(ByteArray(sampleCount(options)))
            "option" -> sample(type.getOrNull(1) ?: JsonPrimitive("void"), context)
            "container" -> ProtocolValue.obj(sampleContainer(type.fields(), context))
            "switch" -> sampleSwitch(options, context)
            "mapper" -> ProtocolValue(
                options["mappings"]?.jsonObject?.get("0")?.jsonPrimitive?.content
                    ?: sample(options["type"] ?: JsonPrimitive("varint"), context).value
            )
            "bitflags" -> ProtocolValue(emptyList<String>())
            "bitfield" -> ProtocolValue.obj(type.getOrNull(1)?.jsonArray.orEmpty().associate { field ->
                field.jsonObject["name"]!!.jsonPrimitive.content to ProtocolValue(0)
            })
            "pstring" -> ProtocolValue("sample")
            "registryEntryHolder" -> ProtocolValue.obj(mapOf(options["baseName"]!!.jsonPrimitive.content to ProtocolValue(1)))
            "registryEntryHolderSet" -> {
                val otherwise = options["otherwise"]?.jsonObject
                val name = otherwise?.get("name")?.jsonPrimitive?.content ?: "ids"
                ProtocolValue.obj(mapOf(name to ProtocolValue.list(listOf(sample(otherwise?.get("type") ?: JsonPrimitive("varint"), context)))))
            }
            "entityMetadataLoop" -> ProtocolValue.bytes(byteArrayOf(0xff.toByte()))
            "void" -> ProtocolValue(null)
            else -> ProtocolValue(null)
        }
    }

    private suspend fun encodeContainer(
        fields: List<JsonObject>,
        values: Map<String, ProtocolValue>,
        channel: ByteWriteChannel,
        parentContext: MutableMap<String, Any?>,
    ) {
        val localContext = parentContext.toMutableMap()
        for (field in fields) {
            val fieldType = field["type"] ?: JsonPrimitive("void")
            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            val name = field["name"]?.jsonPrimitive?.content
            val fieldValue = if (name != null) values[name] ?: sample(fieldType, localContext) else ProtocolValue.obj(values)

            encode(fieldType, fieldValue, channel, localContext)

            if (anonymous && fieldValue.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                localContext.putAll((fieldValue.value as Map<String, ProtocolValue>).mapValues { it.value.value })
            } else if (name != null) {
                localContext[name] = fieldValue.value
            }
        }
    }

    private suspend fun decodeContainer(
        fields: List<JsonObject>,
        channel: ByteReadChannel,
        parentContext: MutableMap<String, Any?>,
    ): Map<String, ProtocolValue> {
        val values = linkedMapOf<String, ProtocolValue>()
        val localContext = parentContext.toMutableMap()
        for (field in fields) {
            val fieldType = field["type"] ?: JsonPrimitive("void")
            val decoded = decode(fieldType, channel, localContext)
            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            val name = field["name"]?.jsonPrimitive?.content
            if (anonymous && decoded.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val decodedMap = decoded.value as Map<String, ProtocolValue>
                values.putAll(decodedMap)
                localContext.putAll(decodedMap.mapValues { it.value.value })
            } else if (name != null) {
                values[name] = decoded
                localContext[name] = decoded.value
            }
        }
        return values
    }

    private fun sampleContainer(fields: List<JsonObject>, parentContext: MutableMap<String, Any?>): Map<String, ProtocolValue> {
        val values = linkedMapOf<String, ProtocolValue>()
        val localContext = parentContext.toMutableMap()
        for (field in fields) {
            val fieldType = field["type"] ?: JsonPrimitive("void")
            val decoded = sample(fieldType, localContext)
            val anonymous = field["anon"]?.jsonPrimitive?.booleanOrNull == true
            val name = field["name"]?.jsonPrimitive?.content
            if (anonymous && decoded.value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val decodedMap = decoded.value as Map<String, ProtocolValue>
                values.putAll(decodedMap)
                localContext.putAll(decodedMap.mapValues { it.value.value })
            } else if (name != null) {
                values[name] = decoded
                localContext[name] = decoded.value
            }
        }
        return values
    }

    private suspend fun encodeSwitch(
        options: JsonObject,
        value: ProtocolValue,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val selected = selectedSwitchType(options, context) ?: return
        encode(selected, value, channel, context)
    }

    private suspend fun decodeSwitch(options: JsonObject, channel: ByteReadChannel, context: MutableMap<String, Any?>): ProtocolValue {
        val selected = selectedSwitchType(options, context) ?: return ProtocolValue(null)
        return decode(selected, channel, context)
    }

    private fun sampleSwitch(options: JsonObject, context: MutableMap<String, Any?>): ProtocolValue {
        val selected = selectedSwitchType(options, context) ?: return ProtocolValue(null)
        return sample(selected, context)
    }

    private fun selectedSwitchType(options: JsonObject, context: Map<String, Any?>): JsonElement? {
        val compareTo = options["compareTo"]?.jsonPrimitive?.content ?: return options["default"]
        val compared = context.pathValue(compareTo)
        val fields = options["fields"]?.jsonObject.orEmpty()
        return fields[switchKey(compared)] ?: options["default"]
    }

    private suspend fun encodeBitFlags(
        options: JsonObject,
        value: ProtocolValue,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val flags = options["flags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val selected = value.asStringList().toSet()
        val mask = flags.foldIndexed(0) { index, acc, flag -> if (flag in selected) acc or (1 shl index) else acc }
        encode(options["type"] ?: JsonPrimitive("varint"), ProtocolValue(mask), channel, context)
    }

    private suspend fun decodeBitFlags(
        options: JsonObject,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): ProtocolValue {
        val mask = (decode(options["type"] ?: JsonPrimitive("varint"), channel, context).value as Number).toInt()
        val flags = options["flags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        return ProtocolValue(flags.filterIndexed { index, _ -> mask and (1 shl index) != 0 })
    }

    private suspend fun encodeBitField(fields: List<JsonElement>, values: Map<String, ProtocolValue>, channel: ByteWriteChannel) {
        var packed = 0L
        var offset = fields.sumOf { it.jsonObject["size"]!!.jsonPrimitive.int }
        fields.forEach { fieldElement ->
            val field = fieldElement.jsonObject
            val size = field["size"]!!.jsonPrimitive.int
            offset -= size
            val name = field["name"]!!.jsonPrimitive.content
            val mask = if (size == 64) -1L else (1L shl size) - 1
            packed = packed or ((values[name].asNumber().toLong() and mask) shl offset)
        }
        writeSizedInteger(channel, packed, fields.sumOf { it.jsonObject["size"]!!.jsonPrimitive.int })
    }

    private suspend fun decodeBitField(fields: List<JsonElement>, channel: ByteReadChannel): Map<String, ProtocolValue> {
        val totalBits = fields.sumOf { it.jsonObject["size"]!!.jsonPrimitive.int }
        val packed = readSizedInteger(channel, totalBits)
        var offset = totalBits
        return fields.associate { fieldElement ->
            val field = fieldElement.jsonObject
            val size = field["size"]!!.jsonPrimitive.int
            offset -= size
            val signed = field["signed"]?.jsonPrimitive?.booleanOrNull == true
            val mask = if (size == 64) -1L else (1L shl size) - 1
            val raw = (packed ushr offset) and mask
            val value = if (signed && size < 64 && raw and (1L shl (size - 1)) != 0L) raw or (-1L shl size) else raw
            field["name"]!!.jsonPrimitive.content to ProtocolValue(value)
        }
    }

    private suspend fun encodeTopBitSetArray(
        elementType: JsonElement,
        values: List<ProtocolValue>,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        values.forEachIndexed { index, element ->
            val last = index == values.lastIndex
            val map = element.asObject().toMutableMap()
            val firstField = (elementType as? JsonArray)?.fields()?.firstOrNull()?.get("name")?.jsonPrimitive?.content
            if (firstField != null) {
                val raw = map[firstField].asNumber().toInt()
                map[firstField] = ProtocolValue(if (last) raw and 0x7f else raw or 0x80)
            }
            encode(elementType, ProtocolValue.obj(map), channel, context)
        }
    }

    private suspend fun decodeTopBitSetArray(
        elementType: JsonElement,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): ProtocolValue {
        val values = mutableListOf<ProtocolValue>()
        val fields = (elementType as? JsonArray)?.fields()
        val firstField = fields?.firstOrNull()
        if (fields == null || firstField?.get("type")?.jsonPrimitive?.content !in setOf("i8", "u8")) return ProtocolValue.list(values)
        val firstFieldValue = firstField ?: return ProtocolValue.list(values)

        do {
            val firstByte = channel.readByte().toInt()
            val hasMore = firstByte and 0x80 != 0
            val firstName = firstFieldValue["name"]!!.jsonPrimitive.content
            val localContext = context.toMutableMap()
            val map = linkedMapOf(firstName to ProtocolValue(firstByte and 0x7f))
            localContext[firstName] = firstByte and 0x7f
            fields.drop(1).forEach { field ->
                val name = field["name"]?.jsonPrimitive?.content ?: return@forEach
                val decoded = decode(field["type"] ?: JsonPrimitive("void"), channel, localContext)
                map[name] = decoded
                localContext[name] = decoded.value
            }
            values += ProtocolValue.obj(map)
        } while (hasMore)

        return ProtocolValue.list(values)
    }

    private suspend fun encodeRegistryEntryHolder(
        options: JsonObject,
        values: Map<String, ProtocolValue>,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val baseName = options["baseName"]!!.jsonPrimitive.content
        val otherwise = options["otherwise"]!!.jsonObject
        val otherwiseName = otherwise["name"]!!.jsonPrimitive.content
        val directValue = values[otherwiseName]
        if (directValue?.value != null) {
            channel.writeVarInt(0)
            encode(otherwise["type"] ?: JsonPrimitive("void"), directValue, channel, context)
        } else {
            channel.writeVarInt(values[baseName].asNumber().toInt() + 1)
        }
    }

    private suspend fun decodeRegistryEntryHolder(
        options: JsonObject,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): Map<String, ProtocolValue> {
        val baseName = options["baseName"]!!.jsonPrimitive.content
        val rawId = channel.readVarInt()
        val values = linkedMapOf<String, ProtocolValue>()
        if (rawId == 0) {
            val otherwise = options["otherwise"]!!.jsonObject
            val name = otherwise["name"]!!.jsonPrimitive.content
            values[name] = decode(otherwise["type"] ?: JsonPrimitive("void"), channel, context)
        } else {
            values[baseName] = ProtocolValue(rawId - 1)
        }
        return values
    }

    private suspend fun encodeRegistryEntryHolderSet(
        options: JsonObject,
        values: Map<String, ProtocolValue>,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val base = options["base"]!!.jsonObject
        val baseName = base["name"]!!.jsonPrimitive.content
        val baseValue = values[baseName]
        if (baseValue?.value != null) {
            channel.writeVarInt(0)
            encode(base["type"] ?: JsonPrimitive("string"), baseValue, channel, context)
            return
        }

        val otherwise = options["otherwise"]!!.jsonObject
        val otherwiseName = otherwise["name"]!!.jsonPrimitive.content
        val ids = values[otherwiseName]?.asList().orEmpty()
        channel.writeVarInt(ids.size + 1)
        ids.forEach { encode(otherwise["type"] ?: JsonPrimitive("varint"), it, channel, context) }
    }

    private suspend fun decodeRegistryEntryHolderSet(
        options: JsonObject,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): Map<String, ProtocolValue> {
        val tagOrCount = channel.readVarInt()
        val values = linkedMapOf<String, ProtocolValue>()
        if (tagOrCount == 0) {
            val base = options["base"]!!.jsonObject
            values[base["name"]!!.jsonPrimitive.content] = decode(base["type"] ?: JsonPrimitive("string"), channel, context)
        } else {
            val otherwise = options["otherwise"]!!.jsonObject
            val type = otherwise["type"] ?: JsonPrimitive("varint")
            values[otherwise["name"]!!.jsonPrimitive.content] = ProtocolValue.list(
                List(tagOrCount - 1) { decode(type, channel, context) }
            )
        }
        return values
    }

    private suspend fun encodeScalar(name: String, value: Any?, channel: ByteWriteChannel) {
        when (name) {
            "void" -> Unit
            "bool" -> channel.writeByte(if (value == true) 1 else 0)
            "i8" -> channel.writeByte((value as? Number)?.toByte() ?: 0)
            "u8" -> channel.writeByte((value as? Number)?.toInt()?.toByte() ?: 0)
            "i16" -> channel.writeShort((value as? Number)?.toShort() ?: 0)
            "u16" -> channel.writeShort((value as? Number)?.toInt()?.toShort() ?: 0)
            "i32" -> channel.writeInt((value as? Number)?.toInt() ?: 0)
            "u32" -> channel.writeInt((value as? Number)?.toLong()?.toInt() ?: 0)
            "varint", "optvarint" -> channel.writeVarInt((value as? Number)?.toInt() ?: 0)
            "i64" -> channel.writeLong((value as? Number)?.toLong() ?: 0L)
            "varlong" -> channel.writeVarLong((value as? Number)?.toLong() ?: 0L)
            "f32" -> channel.writeFloat((value as? Number)?.toFloat() ?: 0f)
            "f64" -> channel.writeDouble((value as? Number)?.toDouble() ?: 0.0)
            "string" -> writeString(channel, value as? String ?: "")
            "UUID" -> writeUuid(channel, value)
            "restBuffer" -> channel.writeFully((value as? ByteArray) ?: byteArrayOf())
            "anonymousNbt" -> channel.writeNbtTag((value as? NbtTag) ?: emptyNbt)
            "anonOptionalNbt" -> {
                val tag = value as? NbtTag
                if (tag == null) channel.writeByte(NBT_END.toByte()) else channel.writeNbtTag(tag)
            }
            "entityMetadataLoop" -> channel.writeFully((value as? ByteArray) ?: byteArrayOf(0xff.toByte()))
            else -> error("Unsupported scalar protocol type: $name")
        }
    }

    private suspend fun decodeScalar(name: String, channel: ByteReadChannel): Any? =
        when (name) {
            "void" -> null
            "bool" -> channel.readByte().toInt() != 0
            "i8" -> channel.readByte()
            "u8" -> channel.readByte().toUByte().toShort()
            "i16" -> channel.readShort()
            "u16" -> channel.readShort().toUShort().toInt()
            "i32" -> channel.readInt()
            "u32" -> channel.readInt().toUInt().toLong()
            "varint", "optvarint" -> channel.readVarInt()
            "i64" -> channel.readLong()
            "varlong" -> channel.readVarLong()
            "f32" -> channel.readFloat()
            "f64" -> channel.readDouble()
            "string" -> readString(channel)
            "UUID" -> UUID(channel.readLong(), channel.readLong()).toString()
            "restBuffer" -> channel.readRemaining().readByteArray()
            "anonymousNbt" -> channel.readNbtTag() ?: emptyNbt
            "anonOptionalNbt" -> channel.readNbtTag()
            "entityMetadataLoop" -> byteArrayOf(channel.readByte())
            else -> error("Unsupported scalar protocol type: $name")
        }

    private fun sampleScalar(name: String): ProtocolValue =
        ProtocolValue(
            when (name) {
                "void" -> null
                "bool" -> true
                "i8", "u8", "i16", "u16", "i32", "u32", "varint", "optvarint" -> 0
                "i64", "varlong" -> 0L
                "f32" -> 0f
                "f64" -> 0.0
                "string" -> "sample"
                "UUID" -> "00000000-0000-0000-0000-000000000001"
                "restBuffer" -> byteArrayOf()
                "anonymousNbt" -> emptyNbt
                "anonOptionalNbt" -> null
                "entityMetadataLoop" -> byteArrayOf(0xff.toByte())
                else -> null
            }
        )

    private suspend fun decodeMapper(options: JsonObject, channel: ByteReadChannel, context: MutableMap<String, Any?>): Any? {
        val raw = decode(options["type"] ?: JsonPrimitive("varint"), channel, context).value as Number
        return options["mappings"]?.jsonObject?.get(raw.toString())?.jsonPrimitive?.content ?: raw
    }

    private fun ProtocolValue.mapperValue(options: JsonObject): Any? {
        val mappings = options["mappings"]?.jsonObject.orEmpty()
        val value = value
        if (value is String) {
            return mappings.entries.firstOrNull { it.value.jsonPrimitive.content == value }?.key?.toIntOrNull() ?: 0
        }
        return value
    }

    private fun readArrayCount(options: JsonObject, context: Map<String, Any?>): Int {
        val count = options["count"]
        if (count is JsonPrimitive) {
            count.intOrNull?.let { return it }
            context[count.content]?.let { return (it as Number).toInt() }
        }
        return -1
    }

    private fun sampleCount(options: JsonObject): Int =
        (options["count"] as? JsonPrimitive)?.intOrNull ?: 0

    private suspend fun readArrayCount(options: JsonObject, context: MutableMap<String, Any?>, channel: ByteReadChannel): Int {
        val known = readArrayCount(options, context as Map<String, Any?>)
        if (known >= 0) return known
        return readCount(options["countType"], channel)
    }

    private suspend fun readCount(type: JsonElement?, channel: ByteReadChannel): Int {
        val actualChannel = channel
        return when ((type as? JsonPrimitive)?.content ?: "varint") {
            "u8" -> actualChannel.readByte().toUByte().toInt()
            "i8" -> actualChannel.readByte().toInt()
            "u16" -> actualChannel.readShort().toUShort().toInt()
            "i16" -> actualChannel.readShort().toInt()
            "i32" -> actualChannel.readInt()
            else -> actualChannel.readVarInt()
        }
    }

    private suspend fun writeCount(type: JsonElement?, count: Int, channel: ByteWriteChannel) {
        when ((type as? JsonPrimitive)?.content ?: "varint") {
            "u8", "i8" -> channel.writeByte(count.toByte())
            "u16", "i16" -> channel.writeShort(count.toShort())
            "i32" -> channel.writeInt(count)
            else -> channel.writeVarInt(count)
        }
    }

    private suspend fun writeString(channel: ByteWriteChannel, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        channel.writeVarInt(bytes.size)
        channel.writeFully(bytes)
    }

    private suspend fun readString(channel: ByteReadChannel): String {
        val bytes = ByteArray(channel.readVarInt())
        channel.readFully(bytes, 0, bytes.size)
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun writeUuid(channel: ByteWriteChannel, value: Any?) {
        val uuid = when (value) {
            is UUID -> value
            is String -> UUID.fromString(value)
            else -> UUID(0L, 0L)
        }
        channel.writeLong(uuid.mostSignificantBits)
        channel.writeLong(uuid.leastSignificantBits)
    }

    private suspend fun writeSizedInteger(channel: ByteWriteChannel, value: Long, bits: Int) {
        when {
            bits <= 8 -> channel.writeByte(value.toByte())
            bits <= 16 -> channel.writeShort(value.toShort())
            bits <= 32 -> channel.writeInt(value.toInt())
            else -> channel.writeLong(value)
        }
    }

    private suspend fun readSizedInteger(channel: ByteReadChannel, bits: Int): Long =
        when {
            bits <= 8 -> channel.readByte().toLong() and 0xff
            bits <= 16 -> channel.readShort().toLong() and 0xffff
            bits <= 32 -> channel.readInt().toLong() and 0xffffffffL
            else -> channel.readLong()
        }

    private fun switchKey(value: Any?): String =
        when (value) {
            is Boolean -> value.toString()
            is Number -> value.toLong().toString()
            is ProtocolValue -> switchKey(value.value)
            else -> value?.toString() ?: ""
        }

    private fun Map<String, Any?>.pathValue(path: String): Any? {
        val parts = path.removePrefix("../").split('/').filter { it.isNotEmpty() && it != ".." }
        if (parts.isEmpty()) return null

        var current: Any? = this[parts.first()]
        parts.drop(1).forEach { part ->
            current = when (val value = current.unwrapProtocolValue()) {
                is Map<*, *> -> value[part]
                else -> return null
            }
        }
        return current.unwrapProtocolValue()
    }

    private fun Any?.unwrapProtocolValue(): Any? =
        if (this is ProtocolValue) value else this

    private fun JsonArray.kind(): String = this[0].jsonPrimitive.content
    private fun JsonArray.options(): JsonObject = getOrNull(1) as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonArray.fields(): List<JsonObject> = getOrNull(1)?.jsonArray?.map { it.jsonObject }.orEmpty()

    private fun ProtocolValue.asObject(): Map<String, ProtocolValue> {
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, ProtocolValue> ?: emptyMap()
    }

    private fun ProtocolValue.asList(): List<ProtocolValue> {
        @Suppress("UNCHECKED_CAST")
        return value as? List<ProtocolValue> ?: emptyList()
    }

    private fun ProtocolValue.asStringList(): List<String> =
        (value as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun ProtocolValue.asBytes(): ByteArray = value as? ByteArray ?: byteArrayOf()
    private fun ProtocolValue?.asNumber(): Number = this?.value as? Number ?: 0

    private fun Any?.toProtocolValue(): ProtocolValue =
        when (this) {
            null -> ProtocolValue(null)
            is ProtocolValue -> this
            is NbtTag -> ProtocolValue(this)
            is ByteArray -> ProtocolValue.bytes(this)
            is Iterable<*> -> ProtocolValue.list(map { it.toProtocolValue() })
            is Map<*, *> -> ProtocolValue.obj(entries.associate { it.key.toString() to it.value.toProtocolValue() })
            is UUID -> ProtocolValue(toString())
            is Enum<*> -> ProtocolValue(this::class.java.getField(name).getAnnotation(SerialName::class.java)?.value ?: name)
            is String, is Number, is Boolean -> ProtocolValue(this)
            else -> ProtocolValue.obj(
                this::class.memberProperties.associate { property ->
                    property.name to property.getter.call(this).toProtocolValue()
                }
            )
        }

    private fun resolveAlias(type: JsonElement, context: Map<String, Any?>): JsonElement? {
        val name = (type as? JsonPrimitive)?.contentOrNull ?: return null
        if (name in supportedScalarTypes || name == "void") return null

        val alias = protocolTypes(context)[name] ?: return null
        if ((alias as? JsonPrimitive)?.contentOrNull == "native") return null
        return alias
    }

    private val supportedScalarTypes = setOf(
        "bool",
        "i8",
        "u8",
        "i16",
        "u16",
        "i32",
        "u32",
        "varint",
        "optvarint",
        "i64",
        "varlong",
        "f32",
        "f64",
        "string",
        "UUID",
        "restBuffer",
        "anonymousNbt",
        "anonOptionalNbt",
        "entityMetadataLoop",
    )

    private val protocolTypesByResourcePath = ConcurrentHashMap<String, Map<String, JsonElement>>()

    private fun protocolTypes(context: Map<String, Any?>): Map<String, JsonElement> =
        protocolTypesByResourcePath.computeIfAbsent(context.protocolResourcePath()) { resourcePath ->
            loadProtocolTypes(resourcePath)
        }

    private fun Map<String, Any?>.protocolResourcePath(): String {
        val resourcePath = (this[ProtocolResourcePathContextKey] as? String)
            ?: protocolResourcePath
        return resourcePath.takeIf { it.isNotBlank() }
            ?: error(
                "ProtocolDynamicCodec requires a protocolResourcePath. Use generated ProtocolData or pass " +
                    "protocolResourcePath to ProtocolDynamicCodec directly."
            )
    }

    private fun loadProtocolTypes(resourcePath: String): Map<String, JsonElement> {
        val resource = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: ProtocolDynamicCodec::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing bundled minecraft-data protocol.json resource: $resourcePath")

        return resource.use {
            val protocol = Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject
            buildMap {
                putAll(protocol["types"]!!.jsonObject)
                listOf("handshaking", "status", "login", "configuration", "play").forEach { state ->
                    val stateObject = protocol[state]?.jsonObject ?: return@forEach
                    listOf("toServer", "toClient").forEach { direction ->
                        putAll(stateObject[direction]?.jsonObject?.get("types")?.jsonObject.orEmpty())
                    }
                }
            }
        }
    }

    private val emptyNbt = NbtCompound(emptyMap())

    private const val ProtocolResourcePathContextKey = "\u0000kmc.protocolResourcePath"

    private const val NBT_END = 0
    private const val NBT_BYTE = 1
    private const val NBT_SHORT = 2
    private const val NBT_INT = 3
    private const val NBT_LONG = 4
    private const val NBT_FLOAT = 5
    private const val NBT_DOUBLE = 6
    private const val NBT_BYTE_ARRAY = 7
    private const val NBT_STRING = 8
    private const val NBT_LIST = 9
    private const val NBT_COMPOUND = 10
    private const val NBT_INT_ARRAY = 11
    private const val NBT_LONG_ARRAY = 12

    private suspend fun ByteWriteChannel.writeNbtTag(tag: NbtTag) {
        writeByte(tag.nbtTypeId().toByte())
        writeNbtPayload(tag)
    }

    private suspend fun ByteWriteChannel.writeNbtPayload(tag: NbtTag) {
        when (tag) {
            is NbtByte -> writeByte(tag.value)
            is NbtShort -> writeShort(tag.value)
            is NbtInt -> writeInt(tag.value)
            is NbtLong -> writeLong(tag.value)
            is NbtFloat -> writeFloat(tag.value)
            is NbtDouble -> writeDouble(tag.value)
            is NbtByteArray -> {
                writeInt(tag.size)
                tag.forEach { writeByte(it) }
            }
            is NbtString -> writeNbtString(tag.value)
            is NbtList<*> -> {
                val elementType = tag.firstOrNull()?.nbtTypeId() ?: NBT_END
                writeByte(elementType.toByte())
                writeInt(tag.size)
                tag.forEach { writeNbtPayload(it) }
            }
            is NbtCompound -> {
                tag.forEach { (name, value) ->
                    writeByte(value.nbtTypeId().toByte())
                    writeNbtString(name)
                    writeNbtPayload(value)
                }
                writeByte(NBT_END.toByte())
            }
            is NbtIntArray -> {
                writeInt(tag.size)
                tag.forEach { writeInt(it) }
            }
            is NbtLongArray -> {
                writeInt(tag.size)
                tag.forEach { writeLong(it) }
            }
        }
    }

    private suspend fun ByteReadChannel.readNbtTag(): NbtTag? {
        val type = readByte().toInt() and 0xff
        return if (type == NBT_END) null else readNbtPayload(type)
    }

    private suspend fun ByteReadChannel.readNbtPayload(type: Int): NbtTag =
        when (type) {
            NBT_BYTE -> NbtByte(readByte())
            NBT_SHORT -> NbtShort(readShort())
            NBT_INT -> NbtInt(readInt())
            NBT_LONG -> NbtLong(readLong())
            NBT_FLOAT -> NbtFloat(readFloat())
            NBT_DOUBLE -> NbtDouble(readDouble())
            NBT_BYTE_ARRAY -> NbtByteArray(ByteArray(readInt()) { readByte() })
            NBT_STRING -> NbtString(readNbtString())
            NBT_LIST -> readNbtList()
            NBT_COMPOUND -> readNbtCompound()
            NBT_INT_ARRAY -> NbtIntArray(IntArray(readInt()) { readInt() })
            NBT_LONG_ARRAY -> NbtLongArray(LongArray(readInt()) { readLong() })
            else -> error("Unsupported NBT tag type id: $type")
        }

    private suspend fun ByteReadChannel.readNbtList(): NbtList<*> {
        val elementType = readByte().toInt() and 0xff
        val count = readInt()
        val values = List(count) { readNbtPayload(elementType) }
        return values.toNbtList(elementType)
    }

    private suspend fun ByteReadChannel.readNbtCompound(): NbtCompound {
        val values = linkedMapOf<String, NbtTag>()
        while (true) {
            val type = readByte().toInt() and 0xff
            if (type == NBT_END) return NbtCompound(values)
            values[readNbtString()] = readNbtPayload(type)
        }
    }

    private fun List<NbtTag>.toNbtList(elementType: Int): NbtList<*> =
        when (elementType) {
            NBT_BYTE, NBT_END -> NbtList(filterIsInstance<NbtByte>())
            NBT_SHORT -> NbtList(filterIsInstance<NbtShort>())
            NBT_INT -> NbtList(filterIsInstance<NbtInt>())
            NBT_LONG -> NbtList(filterIsInstance<NbtLong>())
            NBT_FLOAT -> NbtList(filterIsInstance<NbtFloat>())
            NBT_DOUBLE -> NbtList(filterIsInstance<NbtDouble>())
            NBT_BYTE_ARRAY -> NbtList(filterIsInstance<NbtByteArray>())
            NBT_STRING -> NbtList(filterIsInstance<NbtString>())
            NBT_LIST -> NbtList(filterIsInstance<NbtList<*>>())
            NBT_COMPOUND -> NbtList(filterIsInstance<NbtCompound>())
            NBT_INT_ARRAY -> NbtList(filterIsInstance<NbtIntArray>())
            NBT_LONG_ARRAY -> NbtList(filterIsInstance<NbtLongArray>())
            else -> error("Unsupported NBT list element type id: $elementType")
        }

    private fun NbtTag.nbtTypeId(): Int =
        when (this) {
            is NbtByte -> NBT_BYTE
            is NbtShort -> NBT_SHORT
            is NbtInt -> NBT_INT
            is NbtLong -> NBT_LONG
            is NbtFloat -> NBT_FLOAT
            is NbtDouble -> NBT_DOUBLE
            is NbtByteArray -> NBT_BYTE_ARRAY
            is NbtString -> NBT_STRING
            is NbtList<*> -> NBT_LIST
            is NbtCompound -> NBT_COMPOUND
            is NbtIntArray -> NBT_INT_ARRAY
            is NbtLongArray -> NBT_LONG_ARRAY
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any?.directNbtValue(deserializer: DeserializationStrategy<T>): T? =
        if (this is NbtTag && deserializer.descriptor.serialName.startsWith("net.benwoodworth.knbt.")) this as T else null

    private suspend fun ByteWriteChannel.writeNbtString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size.toShort())
        writeFully(bytes)
    }

    private suspend fun ByteReadChannel.readNbtString(): String {
        val bytes = ByteArray(readShort().toUShort().toInt())
        readFully(bytes, 0, bytes.size)
        return bytes.toString(Charsets.UTF_8)
    }
}
