package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

internal class MinecraftDataParser(private val spec: MinecraftDataSpec) {

    fun parse(): MinecraftDataModel = MinecraftDataModel(
        minecraftVersion = spec.minecraftVersion,
        blocks = spec.blocksJson.readArray().map(::parseBlock),
        items = spec.itemsJson.readArray().map(::parseItem),
        entities = spec.entitiesJson.readArray().map(::parseEntity),
    )

    private fun parseBlock(element: JsonElement): BlockModel {
        val block = element.asObject()
        val minStateId = block.int("minStateId") ?: 0
        val maxStateId = block.int("maxStateId") ?: minStateId
        return BlockModel(
            id = block.requireInt("id"),
            name = block.requireString("name"),
            displayName = block.string("displayName") ?: block.requireString("name"),
            hardness = block.double("hardness"),
            resistance = block.double("resistance") ?: 0.0,
            stackSize = block.int("stackSize") ?: 64,
            diggable = block.boolean("diggable") ?: true,
            material = block.string("material"),
            transparent = block.boolean("transparent") ?: false,
            emitLight = block.int("emitLight") ?: 0,
            filterLight = block.int("filterLight") ?: 0,
            defaultState = block.int("defaultState") ?: minStateId,
            minStateId = minStateId,
            maxStateId = maxStateId,
            boundingBox = block.string("boundingBox") ?: "",
            states = parseStates(block.array("states")),
            harvestTools = parseHarvestTools(block["harvestTools"]),
            drops = block.array("drops").map(::parseDrop),
            variations = block.array("variations").map(::parseVariation),
        )
    }

    private fun parseStates(states: List<JsonElement>): List<StatePropertyModel> {
        val parsed = states.map { element ->
            val state = element.asObject()
            val type = state.string("type") ?: "enum"
            val numValues = state.int("num_values") ?: 0
            val values = state.array("values").mapNotNull { it.asPrimitive()?.content }
            StatePropertyModel(
                name = state.requireString("name"),
                type = type,
                numValues = numValues,
                values = when {
                    values.isNotEmpty() -> values
                    type == "bool" && numValues == BooleanValues.size -> BooleanValues
                    else -> List(numValues) { it.toString() }
                },
                stride = 1,
            )
        }

        var stride = 1
        val strides = IntArray(parsed.size)
        for (index in parsed.indices.reversed()) {
            strides[index] = stride
            stride *= parsed[index].numValues.coerceAtLeast(1)
        }
        return parsed.mapIndexed { index, state -> state.copy(stride = strides[index]) }
    }

    private fun parseHarvestTools(element: JsonElement?): List<Int> {
        val tools = element as? JsonObject ?: return emptyList()
        return tools.entries
            .filter { (_, value) -> value.asPrimitive()?.content?.toBooleanStrictOrNull() != false }
            .mapNotNull { (key, _) -> key.toIntOrNull() }
            .sorted()
    }

    private fun parseDrop(element: JsonElement): DropModel {
        element.asPrimitive()?.content?.toIntOrNull()?.let { return DropModel(it, null, null, null) }

        val drop = element.asObject()
        val target = drop["drop"]
        val itemId = target?.asPrimitive()?.content?.toIntOrNull()
            ?: (target as? JsonObject)?.int("id")
            ?: 0
        return DropModel(
            itemId = itemId,
            metadata = (target as? JsonObject)?.int("metadata"),
            minCount = drop.double("minCount"),
            maxCount = drop.double("maxCount"),
        )
    }

    private fun parseVariation(element: JsonElement): VariationModel {
        val variation = element.asObject()
        return VariationModel(
            metadata = variation.int("metadata") ?: 0,
            displayName = variation.string("displayName") ?: "",
            description = variation.string("description"),
        )
    }

    private fun parseItem(element: JsonElement): ItemModel {
        val item = element.asObject()
        return ItemModel(
            id = item.requireInt("id"),
            name = item.requireString("name"),
            displayName = item.string("displayName") ?: item.requireString("name"),
            stackSize = item.int("stackSize") ?: 64,
            maxDurability = item.int("maxDurability"),
            enchantCategories = item.array("enchantCategories").mapNotNull { it.asPrimitive()?.content },
            repairWith = item.array("repairWith").mapNotNull { it.asPrimitive()?.content },
            variations = item.array("variations").map(::parseVariation),
        )
    }

    private fun parseEntity(element: JsonElement): EntityModel {
        val entity = element.asObject()
        return EntityModel(
            id = entity.requireInt("id"),
            internalId = entity.int("internalId"),
            name = entity.requireString("name"),
            displayName = entity.string("displayName") ?: entity.requireString("name"),
            type = entity.string("type") ?: "",
            category = entity.string("category"),
            width = entity.double("width"),
            height = entity.double("height"),
            metadataKeys = entity.array("metadataKeys").mapNotNull { it.asPrimitive()?.content },
        )
    }

    private fun Path?.readArray(): List<JsonElement> {
        val file = this?.toFile()?.takeIf { it.isFile } ?: return emptyList()
        return when (val root = Json.parseToJsonElement(file.readText())) {
            is JsonArray -> root
            else -> throw IllegalStateException("${file.name} for Minecraft ${spec.minecraftVersion} is not an array.")
        }
    }

    private fun JsonElement.asObject(): JsonObject =
        this as? JsonObject ?: throw IllegalStateException(
            "Expected a minecraft-data object for Minecraft ${spec.minecraftVersion} but found $this."
        )

    private fun JsonElement.asPrimitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it != JsonNull }

    private fun JsonObject.array(name: String): List<JsonElement> = this[name] as? JsonArray ?: emptyList()

    private fun JsonObject.string(name: String): String? = this[name]?.asPrimitive()?.content

    private fun JsonObject.int(name: String): Int? = this[name]?.asPrimitive()?.content?.toDoubleOrNull()?.toInt()

    private fun JsonObject.double(name: String): Double? = this[name]?.asPrimitive()?.content?.toDoubleOrNull()

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.asPrimitive()?.content?.toBooleanStrictOrNull()

    private fun JsonObject.requireInt(name: String): Int = int(name)
        ?: throw IllegalStateException("minecraft-data entry for ${spec.minecraftVersion} has no '$name': $this")

    private fun JsonObject.requireString(name: String): String = string(name)
        ?: throw IllegalStateException("minecraft-data entry for ${spec.minecraftVersion} has no '$name': $this")

    private companion object {
        val BooleanValues = listOf("true", "false")
    }
}
