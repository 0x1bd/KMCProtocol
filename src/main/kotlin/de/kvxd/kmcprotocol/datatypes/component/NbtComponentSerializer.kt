package de.kvxd.kmcprotocol.datatypes.component

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.cloudburstmc.nbt.NbtList
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtMapBuilder
import org.cloudburstmc.nbt.NbtType
import org.jetbrains.annotations.Contract
import java.util.*

/**
 * (Roughly) Taken from [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib/blob/8b33173420a1f158ec077c34febcd48ea72d6cf0/protocol/src/main/java/org/geysermc/mcprotocollib/protocol/codec/NbtComponentSerializer.java)
 */
object NbtComponentSerializer {

    private val BOOLEAN_TYPES = setOf(
        "interpret", "bold", "italic", "underlined", "strikethrough", "obfuscated"
    )

    private val COMPONENT_TYPES = listOf(
        "text" to "text",
        "translatable" to "translate",
        "score" to "score",
        "selector" to "selector",
        "keybind" to "keybind",
        "nbt" to "nbt"
    )

    @Contract("null -> null")
    @JvmStatic
    fun tagComponentToJson(tag: Any?): JsonElement? = convertToJson(null, tag)

    @JvmStatic
    fun jsonComponentToTag(component: JsonElement?): Any? = convertToTag(component)

    @Contract("null -> null")
    private fun convertToTag(element: JsonElement?): Any? {
        return when {
            element == null || element.isJsonNull -> null
            element.isJsonObject -> convertJsonObjectToTag(element.asJsonObject)
            element.isJsonArray -> convertJsonArrayToTag(element.asJsonArray)
            element.isJsonPrimitive -> convertJsonPrimitiveToTag(element.asJsonPrimitive)
            else -> throw IllegalArgumentException("Unhandled json type ${element.javaClass.simpleName} with value ${element.asString}")
        }
    }

    private fun convertJsonObjectToTag(jsonObject: JsonObject): NbtMap {
        val tagBuilder = NbtMap.builder()
        jsonObject.entrySet().forEach { (key, value) -> convertObjectEntryToTag(key, value, tagBuilder) }
        addComponentTypeToTag(jsonObject, tagBuilder)
        return tagBuilder.build()
    }

    private fun convertJsonArrayToTag(array: JsonArray): NbtList<*> {
        val listBuilder = array.mapNotNull { convertToTag(it) }
            .map { NbtType.byClass(it.javaClass) to it }
            .let { pairs ->
                val firstType = pairs.firstOrNull()?.first
                if (pairs.all { it.first == firstType }) {
                    NbtListBuilder(firstType!!).apply { pairs.forEach { addUnsafe(it.second) } }
                } else {
                    null
                }
            }

        return listBuilder?.build() ?: array.map { entry ->
            convertToTag(entry)?.let { convertedTag ->
                NbtMap.builder().apply {
                    put("type", "text")
                    if (convertedTag is NbtList<*>) {
                        put("text", "")
                        put("extra", convertedTag)
                    } else {
                        put("text", stringValue(convertedTag))
                    }
                }.build()
            }
        }.let { NbtListBuilder(NbtType.COMPOUND).apply { it.forEach(::add) }.build() }
    }

    private fun convertJsonPrimitiveToTag(primitive: JsonPrimitive): Any {
        return when {
            primitive.isString -> primitive.asString
            primitive.isBoolean -> if (primitive.asBoolean) 1.toByte() else 0.toByte()
            primitive.isNumber -> convertNumberPrimitiveToTag(primitive.asNumber)
            else -> throw IllegalArgumentException("Unhandled json type ${primitive.javaClass.simpleName} with value ${primitive.asString}")
        }
    }

    private fun convertNumberPrimitiveToTag(number: Number): Number {
        return when (number) {
            is Int -> number.toInt()
            is Byte -> number.toByte()
            is Short -> number.toShort()
            is Long -> number.toLong()
            is Double -> number.toDouble()
            is Float -> number.toFloat()
            else -> number.toInt()
        }
    }

    private fun convertObjectEntryToTag(key: String, value: JsonElement, tag: NbtMapBuilder) {
        if (key == "contents" && value.isJsonObject) {
            value.asJsonObject["id"]?.takeIf { it.isJsonPrimitive }?.asString?.let(::parseUUID)?.let { uuid ->
                value.asJsonObject.remove("id")
                (convertToTag(value) as? NbtMap)?.toBuilder()?.apply {
                    put("id", toIntArray(uuid))
                    tag[key] = build()
                }
                return
            }
        }
        tag[key] = convertToTag(value)
    }

    private fun addComponentTypeToTag(jsonObject: JsonObject, tag: NbtMapBuilder) {
        if (!jsonObject.has("type")) {
            COMPONENT_TYPES.firstOrNull { jsonObject.has(it.second) }?.let { tag.put("type", it.first) }
        }
    }

    private fun convertToJson(key: String?, tag: Any?): JsonElement? {
        return when (tag) {
            null -> null
            is NbtMap -> convertNbtMapToJsonObject(key, tag)
            is NbtList<*> -> convertNbtListToJsonArray(tag)
            is Number -> convertNumberToJsonPrimitive(key, tag)
            is String -> JsonPrimitive(tag)
            is ByteArray -> convertByteArrayToJsonArray(tag)
            is IntArray -> convertIntArrayToJsonArray(tag)
            is LongArray -> convertLongArrayToJsonArray(tag)
            else -> throw IllegalArgumentException("Unhandled tag type ${tag.javaClass.simpleName}")
        }
    }

    private fun convertNbtMapToJsonObject(key: String?, tag: NbtMap): JsonObject {
        val jsonObject = JsonObject()
        if (key != "value") removeComponentTypeFromJsonObject(jsonObject)
        tag.entries.forEach { (entryKey, entryValue) -> convertNbtMapEntryToJson(entryKey, entryValue, jsonObject) }
        return jsonObject
    }

    private fun convertNbtListToJsonArray(tag: NbtList<*>): JsonArray {
        return JsonArray().apply { tag.forEach { add(convertToJson(null, it)) } }
    }

    private fun convertNumberToJsonPrimitive(key: String?, tag: Number): JsonPrimitive {
        return if (key != null && BOOLEAN_TYPES.contains(key)) {
            JsonPrimitive(tag.toByte() != 0.toByte())
        } else {
            JsonPrimitive(tag)
        }
    }

    private fun convertByteArrayToJsonArray(array: ByteArray): JsonArray {
        return JsonArray().apply { array.forEach { add(it) } }
    }

    private fun convertIntArrayToJsonArray(array: IntArray): JsonArray {
        return JsonArray().apply { array.forEach { add(it) } }
    }

    private fun convertLongArrayToJsonArray(array: LongArray): JsonArray {
        return JsonArray().apply { array.forEach { add(it) } }
    }

    private fun convertNbtMapEntryToJson(key: String, tag: Any, jsonObject: JsonObject) {
        if (key == "contents" && tag is NbtMap) {
            (tag["id"] as? IntArray)?.let(::fromIntArray)?.let { uuid ->
                (convertToJson(key, tag) as? JsonObject)?.apply {
                    addProperty("id", uuid.toString())
                    jsonObject.add(key, this)
                }
                return
            }
        }
        jsonObject.add(key.ifEmpty { "text" }, convertToJson(key, tag))
    }

    private fun removeComponentTypeFromJsonObject(jsonObject: JsonObject) {
        jsonObject.remove("type")?.takeIf { it.isJsonPrimitive }?.asString?.let { typeString ->
            COMPONENT_TYPES.filter { it.first != typeString }.forEach { jsonObject.remove(it.first) }
        }
    }

    private fun fromIntArray(parts: IntArray): UUID {
        return if (parts.size != 4) {
            UUID(0, 0)
        } else {
            UUID(
                (parts[0].toLong() shl 32) or (parts[1].toLong() and 0xFFFFFFFFL),
                (parts[2].toLong() shl 32) or (parts[3].toLong() and 0xFFFFFFFFL)
            )
        }
    }

    private fun toIntArray(uuid: UUID): IntArray {
        return toIntArray(uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    private fun toIntArray(msb: Long, lsb: Long): IntArray {
        return intArrayOf((msb shr 32).toInt(), msb.toInt(), (lsb shr 32).toInt(), lsb.toInt())
    }

    private fun parseUUID(uuidString: String): UUID? {
        return runCatching { UUID.fromString(uuidString) }.getOrNull()
    }

    private fun stringValue(tag: Any): String {
        return when (tag) {
            is ByteArray -> tag.contentToString()
            is Byte, is Double, is Float, is Int, is Long, is Short -> tag.toString()
            is IntArray -> tag.contentToString()
            is LongArray -> tag.contentToString()
            is String -> tag
            else -> tag.toString()
        }
    }

    private class NbtListBuilder<T>(val type: NbtType<T>) {
        private val list = mutableListOf<T>()

        fun add(value: T) {
            list.add(value)
        }

        fun addUnsafe(value: Any) {
            add(type.tagClass.cast(value))
        }

        fun build(): NbtList<T> = NbtList(type, list)
    }
}