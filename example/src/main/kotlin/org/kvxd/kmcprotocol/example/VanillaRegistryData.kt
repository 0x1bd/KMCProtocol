package org.kvxd.kmcprotocol.example

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.DoubleTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.ListTag
import net.lenni0451.mcstructs.nbt.tags.LongTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
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
            is JsonObject -> CompoundTag(mapValues { (_, value) -> value.toNbtTag() })
            is JsonArray -> ListTag(map { it.toNbtTag() })
            is JsonPrimitive -> toNbtTag()
        }

    private fun JsonPrimitive.toNbtTag(): NbtTag =
        when {
            isString -> StringTag(content)
            booleanOrNull != null -> ByteTag(booleanOrNull!!)
            intOrNull != null -> IntTag(int)
            longOrNull != null -> LongTag(longOrNull!!)
            else -> DoubleTag(double)
        }
}
