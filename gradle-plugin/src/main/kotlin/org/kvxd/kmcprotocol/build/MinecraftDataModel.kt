package org.kvxd.kmcprotocol.build

import java.nio.file.Path

internal data class MinecraftDataSpec(
    val minecraftVersion: String,
    val blocksJson: Path?,
    val itemsJson: Path?,
    val entitiesJson: Path?,
)

internal data class StatePropertyModel(
    val name: String,
    val type: String,
    val numValues: Int,
    val values: List<String>,
    val stride: Int,
)

internal data class DropModel(
    val itemId: Int,
    val metadata: Int?,
    val minCount: Double?,
    val maxCount: Double?,
)

internal data class VariationModel(
    val metadata: Int,
    val displayName: String,
    val description: String?,
)

internal data class BlockModel(
    val id: Int,
    val name: String,
    val displayName: String,
    val hardness: Double?,
    val resistance: Double,
    val stackSize: Int,
    val diggable: Boolean,
    val material: String?,
    val transparent: Boolean,
    val emitLight: Int,
    val filterLight: Int,
    val defaultState: Int,
    val minStateId: Int,
    val maxStateId: Int,
    val boundingBox: String,
    val states: List<StatePropertyModel>,
    val harvestTools: List<Int>,
    val drops: List<DropModel>,
    val variations: List<VariationModel>,
)

internal data class ItemModel(
    val id: Int,
    val name: String,
    val displayName: String,
    val stackSize: Int,
    val maxDurability: Int?,
    val enchantCategories: List<String>,
    val repairWith: List<String>,
    val variations: List<VariationModel>,
)

internal data class EntityModel(
    val id: Int,
    val internalId: Int?,
    val name: String,
    val displayName: String,
    val type: String,
    val category: String?,
    val width: Double?,
    val height: Double?,
    val metadataKeys: List<String>,
)

internal data class MinecraftDataModel(
    val minecraftVersion: String,
    val blocks: List<BlockModel>,
    val items: List<ItemModel>,
    val entities: List<EntityModel>,
)

internal data class MinecraftDataReport(
    val blockCount: Int,
    val itemCount: Int,
    val entityCount: Int,
    val blockStateCount: Int,
)
