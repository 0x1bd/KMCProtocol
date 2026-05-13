package org.kvxd.kmcprotocol.core.encoding

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readDouble
import io.ktor.utils.io.readFloat
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
import java.util.UUID

object ProtocolDynamicCodec {

    fun parse(type: String): JsonElement = Json.parseToJsonElement(type)

    suspend fun encode(type: JsonElement, value: ProtocolValue, channel: ByteWriteChannel, context: MutableMap<String, Any?>) {
        resolveAlias(type)?.let {
            encode(it, value, channel, context)
            return
        }

        when (type) {
            is JsonPrimitive -> encodeScalar(type.content, value.value, channel)
            is JsonArray -> encodeCompound(type, value, channel, context)
            else -> error("Unsupported protocol type shape: $type")
        }
    }

    suspend fun decode(type: JsonElement, channel: ByteReadChannel, context: MutableMap<String, Any?>): ProtocolValue =
        resolveAlias(type)?.let { decode(it, channel, context) } ?:
        when (type) {
            is JsonPrimitive -> ProtocolValue(decodeScalar(type.content, channel))
            is JsonArray -> decodeCompound(type, channel, context)
            else -> error("Unsupported protocol type shape: $type")
        }

    fun sample(type: JsonElement, context: MutableMap<String, Any?> = mutableMapOf()): ProtocolValue =
        resolveAlias(type)?.let { sample(it, context) } ?:
        when (type) {
            is JsonPrimitive -> sampleScalar(type.content)
            is JsonArray -> sampleCompound(type, context)
            else -> ProtocolValue()
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
                channel.readAvailable(bytes, 0, count)
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
            "option" -> ProtocolValue(null)
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
            "registryEntryHolderSet" -> ProtocolValue.obj(mapOf("ids" to ProtocolValue(1)))
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
        val compared = context[compareTo]
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
        val id = values[baseName].asNumber().toInt()
        channel.writeVarInt(id)
        if (id == 0) {
            val otherwise = options["otherwise"]!!.jsonObject
            encode(otherwise["type"] ?: JsonPrimitive("void"), values[otherwise["name"]!!.jsonPrimitive.content] ?: ProtocolValue(), channel, context)
        }
    }

    private suspend fun decodeRegistryEntryHolder(
        options: JsonObject,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): Map<String, ProtocolValue> {
        val baseName = options["baseName"]!!.jsonPrimitive.content
        val id = channel.readVarInt()
        val values = linkedMapOf(baseName to ProtocolValue(id))
        if (id == 0) {
            val otherwise = options["otherwise"]!!.jsonObject
            val name = otherwise["name"]!!.jsonPrimitive.content
            values[name] = decode(otherwise["type"] ?: JsonPrimitive("void"), channel, context)
        }
        return values
    }

    private suspend fun encodeRegistryEntryHolderSet(
        options: JsonObject,
        values: Map<String, ProtocolValue>,
        channel: ByteWriteChannel,
        context: MutableMap<String, Any?>,
    ) {
        val id = values["ids"].asNumber().toInt()
        channel.writeVarInt(id)
        if (id == 0) {
            val base = options["base"]!!.jsonObject
            encode(base["type"] ?: JsonPrimitive("string"), values[base["name"]!!.jsonPrimitive.content] ?: ProtocolValue("sample"), channel, context)
        }
    }

    private suspend fun decodeRegistryEntryHolderSet(
        options: JsonObject,
        channel: ByteReadChannel,
        context: MutableMap<String, Any?>,
    ): Map<String, ProtocolValue> {
        val id = channel.readVarInt()
        val values = linkedMapOf("ids" to ProtocolValue(id))
        if (id == 0) {
            val base = options["base"]!!.jsonObject
            values[base["name"]!!.jsonPrimitive.content] = decode(base["type"] ?: JsonPrimitive("string"), channel, context)
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
            "anonymousNbt", "anonOptionalNbt", "entityMetadataLoop" -> channel.writeFully((value as? ByteArray) ?: byteArrayOf(0))
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
            "anonymousNbt", "anonOptionalNbt", "entityMetadataLoop" -> byteArrayOf(channel.readByte())
            else -> error("Unsupported scalar protocol type: $name")
        }

    private fun sampleScalar(name: String): ProtocolValue =
        ProtocolValue(
            when (name) {
                "void" -> null
                "bool" -> true
                "i8", "u8", "i16", "u16", "i32", "u32", "varint", "optvarint" -> 0
                "i64", "varlong" -> 0L
                "f32" -> 1f
                "f64" -> 1.0
                "string" -> "sample"
                "UUID" -> "00000000-0000-0000-0000-000000000001"
                "restBuffer" -> byteArrayOf()
                "anonymousNbt", "anonOptionalNbt", "entityMetadataLoop" -> byteArrayOf(0)
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
        channel.readAvailable(bytes, 0, bytes.size)
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

    private fun resolveAlias(type: JsonElement): JsonElement? {
        val name = (type as? JsonPrimitive)?.contentOrNull ?: return null
        if (name in supportedScalarTypes || name == "void") return null

        val alias = protocolTypes[name] ?: return null
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

    private val protocolTypes: Map<String, JsonElement> by lazy {
        val resource = Thread.currentThread().contextClassLoader.getResourceAsStream("pc/1.21.4/protocol.json")
            ?: ProtocolDynamicCodec::class.java.classLoader.getResourceAsStream("pc/1.21.4/protocol.json")
            ?: error("Missing bundled minecraft-data protocol.json resource")

        resource.use {
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
}
