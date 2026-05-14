package org.kvxd.kmcprotocol.example

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kvxd.kmcprotocol.packets.generated.types.EntriesEntry
import org.kvxd.kmcprotocol.packets.generated.types.TagsEntry
import org.kvxd.kmcprotocol.packets.generated.types.TagsEntryTagsEntry

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

    val registries: List<Pair<String, List<EntriesEntry>>> by lazy {
        root["registries"]!!.jsonObject.map { (registryId, entriesElement) ->
            registryId to entriesElement.jsonObject.map { (entryId, _) ->
                EntriesEntry(entryId, null)
            }
        }
    }

    val tags: List<TagsEntry> by lazy {
        root["tags"]!!.jsonObject.map { (registryId, tagsElement) ->
            TagsEntry(
                tagType = registryId,
                tags = tagsElement.jsonObject.map { (tagId, entriesElement) ->
                    TagsEntryTagsEntry(
                        tagName = tagId,
                        entries = entriesElement.jsonArray.map { it.jsonPrimitive.int },
                    )
                },
            )
        }
    }
}
