package de.kvxd.kmcprotocol.codec.codecs

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import de.kvxd.kmcprotocol.asDataInput
import de.kvxd.kmcprotocol.asDataOutput
import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import net.kyori.adventure.text.Component
import org.cloudburstmc.nbt.*
import java.io.IOException
import java.util.*

/**
 * Kotlin-ified version of [MCProtocolLib's](https://github.com/GeyserMC/MCProtocolLib/blob/148cd197c24e5cf2781a2405e984de54414eec6f/protocol/src/main/java/org/geysermc/mcprotocollib/protocol/codec/NbtComponentSerializer.java#L27) implementation
 */
object NbtComponentSerializer {

    private val BOOLEAN_TYPES = setOf(
        "interpret",
        "bold",
        "italic",
        "underlined",
        "strikethrough",
        "obfuscated"
    )

    private val COMPONENT_TYPES = listOf(
        Pair("text", "text"),
        Pair("translatable", "translate"),
        Pair("score", "score"),
        Pair("selector", "selector"),
        Pair("keybind", "keybind"),
        Pair("nbt", "nbt")
    )

    fun tagComponentToJson(tag: Any?): JsonElement? = convertToJson(null, tag)

    fun jsonComponentToTag(component: JsonElement?): Any? = convertToTag(component)

    private fun convertToTag(element: JsonElement?): Any? {
        if (element == null || element is JsonNull) return null

        return when {
            element.isJsonObject -> {
                val jsonObject = element.asJsonObject
                val tag = NbtMap.builder()
                for ((key, value) in jsonObject.entrySet()) {
                    convertObjectEntry(key, value, tag)
                }
                addComponentType(jsonObject, tag)
                tag.build()
            }

            element.isJsonArray -> convertJsonArray(element.asJsonArray)
            element.isJsonPrimitive -> {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isString -> primitive.asString
                    primitive.isBoolean -> if (primitive.asBoolean) 1.toByte() else 0.toByte()
                    else -> {
                        val number = primitive.asNumber
                        when (number) {
                            is Int -> number
                            is Byte -> number
                            is Short -> number
                            is Long -> number
                            is Double -> number
                            is Float -> number
                            is LazilyParsedNumber -> number.toInt()
                            else -> number.toInt()
                        }
                    }
                }
            }

            else -> throw IllegalArgumentException("Unhandled json type ${element.javaClass.simpleName} with value ${element.asString}")
        }
    }

    private fun convertJsonArray(array: JsonArray): Any {
        var listBuilder: NbtListBuilder<*>? = null
        for (entry in array) {
            val convertedEntryTag = convertToTag(entry)
            val convertedTagType = NbtType.byClass(convertedEntryTag!!::class.java)

            if (listBuilder == null) {
                listBuilder = NbtListBuilder(convertedTagType)
            }

            if (listBuilder.type != convertedTagType) {
                listBuilder = null
                break
            }

            listBuilder.addUnsafe(convertedEntryTag)
        }

        listBuilder?.let {
            return it.build()
        }

        val processedListTag = NbtListBuilder(NbtType.COMPOUND)
        for (entry in array) {
            val convertedTag = convertToTag(entry)
            if (convertedTag is NbtMap) {
                processedListTag.add(convertedTag)
                continue
            }

            val compoundTag = NbtMap.builder()
            compoundTag.put("type", "text")
            if (convertedTag is NbtList<*>) {
                compoundTag.put("text", "")
                compoundTag.put("extra", convertedTag)
            } else {
                compoundTag.put("text", stringValue(convertedTag))
            }
            processedListTag.add(compoundTag.build())
        }

        return processedListTag.build()
    }

    private fun convertObjectEntry(key: String, value: JsonElement, tag: NbtMapBuilder) {
        if (key == "contents" && value.isJsonObject) {
            val hoverEvent = value.asJsonObject
            val id = hoverEvent.get("id")?.takeIf { it.isJsonPrimitive }?.asString
            id?.let { uuidStr ->
                parseUUID(uuidStr)?.let { uuid ->
                    hoverEvent.remove("id")
                    val convertedTag = (convertToTag(value) as NbtMap).toBuilder()
                    convertedTag.put("id", toIntArray(uuid))
                    tag.put(key, convertedTag.build())
                    return
                }
            }
        }
        tag.put(key, convertToTag(value))
    }

    private fun addComponentType(jsonObject: JsonObject, tag: NbtMapBuilder) {
        if (jsonObject.has("type")) return

        for ((key, value) in COMPONENT_TYPES) {
            if (jsonObject.has(value)) {
                tag.put("type", key)
                return
            }
        }
    }

    private fun convertToJson(key: String?, tag: Any?): JsonElement? {
        if (tag == null) return null

        return when (tag) {
            is NbtMap -> {
                val obj = JsonObject()
                if (key != "value") {
                    removeComponentType(obj)
                }
                for ((entryKey, value) in tag) {
                    convertNbtMapEntry(entryKey, value, obj)
                }
                obj
            }

            is NbtList<*> -> {
                val array = JsonArray()
                tag.forEach { entry -> array.add(convertToJson(null, entry)) }
                array
            }

            is Number -> {
                if (key != null && key in BOOLEAN_TYPES) {
                    JsonPrimitive((tag.toByte() != 0.toByte()))
                } else {
                    JsonPrimitive(tag)
                }
            }

            is String -> JsonPrimitive(tag)
            is ByteArray -> JsonArray().apply { forEach { add(it) } }
            is IntArray -> JsonArray().apply { forEach { add(it) } }
            is LongArray -> JsonArray().apply { forEach { add(it) } }
            else -> throw IllegalArgumentException("Unhandled tag type ${tag.javaClass.simpleName}")
        }
    }

    private fun convertNbtMapEntry(key: String, tag: Any, objectBuilder: JsonObject) {
        if (key == "contents" && tag is NbtMap) {
            val idTag = tag["id"]
            if (idTag is IntArray) {
                val convertedElement = convertToJson(key, tag) as JsonObject
                convertedElement.addProperty("id", fromIntArray(idTag).toString())
                objectBuilder.add(key, convertedElement)
                return
            }
        }
        objectBuilder.add(if (key.isEmpty()) "text" else key, convertToJson(key, tag))
    }

    private fun removeComponentType(objectBuilder: JsonObject) {
        val type = objectBuilder.remove("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return

        COMPONENT_TYPES.forEach { (_, value) ->
            if (value != type) {
                objectBuilder.remove(value)
            }
        }
    }

    private fun fromIntArray(parts: IntArray): UUID {
        if (parts.size != 4) return UUID(0, 0)
        val mostSig = (parts[0].toLong() shl 32) or (parts[1].toLong() and 0xFFFFFFFF)
        val leastSig = (parts[2].toLong() shl 32) or (parts[3].toLong() and 0xFFFFFFFF)
        return UUID(mostSig, leastSig)
    }

    private fun toIntArray(uuid: UUID): IntArray {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        return intArrayOf(
            (msb shr 32).toInt(),
            msb.toInt(),
            (lsb shr 32).toInt(),
            lsb.toInt()
        )
    }

    private fun parseUUID(uuidString: String): UUID? = try {
        UUID.fromString(uuidString)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun stringValue(tag: Any?): String {
        return when (tag) {
            is ByteArray -> tag.contentToString()
            is Byte -> tag.toString()
            is Double -> tag.toString()
            is Float -> tag.toString()
            is IntArray -> tag.contentToString()
            is Int -> tag.toString()
            is LongArray -> tag.contentToString()
            is Long -> tag.toString()
            is Short -> tag.toString()
            is String -> tag
            else -> tag?.toString() ?: ""
        }
    }

    private data class Pair<K, V>(val key: K, val value: V)

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

suspend fun encodeNBT(channel: ByteWriteChannel, tag: Any?) {
    try {
        if (tag == null) {
            channel.writeByte(0.toByte())
            return
        }

        val type = NbtType.byClass(tag.javaClass)
        channel.writeByte(type.id.toByte())

        NBTOutputStream(channel.asDataOutput())
            .writeValue(tag, 512)
    } catch (exception: IOException) {
        throw IllegalArgumentException(exception)
    }
}

suspend fun decodeNBT(channel: ByteReadChannel): Any? {
    try {
        val typeId = channel.readByte()

        if (typeId == 0.toByte())
            return null

        val type = NbtType.byId(typeId.toInt())

        return NBTInputStream(channel.asDataInput()).readValue(type, 512)
    } catch (exception: IOException) {
        throw IllegalArgumentException(exception)
    }
}

object NbtTextCodec : ElementCodec<Component> {

    override suspend fun encode(channel: ByteWriteChannel, value: Component) {
        val json = jsonSerializer.serializeToTree(value)
        val tag = NbtComponentSerializer.jsonComponentToTag(json)

        encodeNBT(channel, tag)
    }

    override suspend fun decode(channel: ByteReadChannel): Component {
        val tag = decodeNBT(channel) ?: throw IllegalArgumentException("Got end-tag when trying to read component")

        val json = NbtComponentSerializer.tagComponentToJson(tag)
        return jsonSerializer.deserializeFromTree(json!!)
    }
}