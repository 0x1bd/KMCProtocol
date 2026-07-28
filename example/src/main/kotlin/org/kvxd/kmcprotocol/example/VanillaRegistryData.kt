package org.kvxd.kmcprotocol.example

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundRegistryDataPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.configuration.clientbound.ClientboundTagsPacket

internal object VanillaRegistryData {
    private const val ResourcePath = "vanilla/1.21.4/synced-registries.json"

    private val root by lazy {
        val resource = Thread.currentThread().contextClassLoader.getResourceAsStream(ResourcePath)
            ?: VanillaRegistryData::class.java.classLoader.getResourceAsStream(ResourcePath)
            ?: error("Missing example registry resource: $ResourcePath")

        resource.use {
            Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject
        }
    }

    val registries: List<Pair<String, List<ClientboundRegistryDataPacket.Entry>>> by lazy {
        root["registries"]!!.jsonObject.map { (registryId, entriesElement) ->
            registryId to entriesElement.jsonObject.map { (entryId, valueElement) ->
                ClientboundRegistryDataPacket.Entry(entryId, valueElement.toNbtTag())
            }
        }
    }

    val tags: List<ClientboundTagsPacket.Tag> by lazy {
        root["tags"]!!.jsonObject.map { (registryId, tagsElement) ->
            ClientboundTagsPacket.Tag(
                tagType = registryId,
                tags = tagsElement.jsonObject.map { (tagId, entriesElement) ->
                    ClientboundTagsPacket.Tag.Entry(
                        tagName = tagId,
                        entries = entriesElement.jsonArray.map { it.jsonPrimitive.int },
                    )
                },
            )
        }
    }

    private fun JsonElement.toNbtTag(): NbtTag =
        when (this) {
            is JsonObject -> NbtCompound(mapValues { (_, value) -> value.toNbtTag() })
            is JsonArray -> toNbtList(map { it.toNbtTag() })
            is JsonPrimitive -> toNbtTag()
        }

    private fun JsonPrimitive.toNbtTag(): NbtTag =
        when {
            isString -> NbtString(content)
            booleanOrNull != null -> NbtByte(booleanOrNull!!)
            intOrNull != null -> NbtInt(int)
            longOrNull != null -> NbtLong(longOrNull!!)
            else -> NbtDouble(double)
        }

    private fun toNbtList(values: List<NbtTag>): NbtList<*> {
        require(values.map { it::class }.distinct().size <= 1) {
            "NBT lists cannot contain mixed tag types: $values"
        }

        return when (values.firstOrNull()) {
            null -> NbtList(emptyList<NbtByte>())
            is NbtByte -> NbtList(values.filterIsInstance<NbtByte>())
            is NbtShort -> NbtList(values.filterIsInstance<NbtShort>())
            is NbtInt -> NbtList(values.filterIsInstance<NbtInt>())
            is NbtLong -> NbtList(values.filterIsInstance<NbtLong>())
            is NbtFloat -> NbtList(values.filterIsInstance<NbtFloat>())
            is NbtDouble -> NbtList(values.filterIsInstance<NbtDouble>())
            is NbtByteArray -> NbtList(values.filterIsInstance<NbtByteArray>())
            is NbtString -> NbtList(values.filterIsInstance<NbtString>())
            is NbtList<*> -> NbtList(values.filterIsInstance<NbtList<*>>())
            is NbtCompound -> NbtList(values.filterIsInstance<NbtCompound>())
            is NbtIntArray -> NbtList(values.filterIsInstance<NbtIntArray>())
            is NbtLongArray -> NbtList(values.filterIsInstance<NbtLongArray>())
        }
    }
}
