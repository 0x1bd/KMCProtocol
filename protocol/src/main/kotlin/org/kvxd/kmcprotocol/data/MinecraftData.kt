package org.kvxd.kmcprotocol.data

import org.kvxd.kmcprotocol.core.KmcProtocolException

class MinecraftDataException(message: String) : KmcProtocolException(message)

interface MinecraftDataSource {
    val minecraftVersion: String

    val data: MinecraftData
}

class MinecraftData(
    val minecraftVersion: String,
    blocks: () -> List<BlockData>,
    items: () -> List<ItemData>,
    entities: () -> List<EntityData>,
) {
    val blocks: List<BlockData> by lazy(blocks)

    val items: List<ItemData> by lazy(items)

    val entities: List<EntityData> by lazy(entities)

    val supportsBlockStates: Boolean by lazy { this.blocks.any { it.maxStateId > 0 } }

    val blockStateCount: Int by lazy {
        if (!supportsBlockStates) 0 else this.blocks.maxOf(BlockData::maxStateId) + 1
    }

    private val blocksById: Map<Int, BlockData> by lazy { this.blocks.associateBy(BlockData::id) }

    private val blocksByName: Map<String, BlockData> by lazy { this.blocks.associateBy(BlockData::name) }

    private val blocksByState: Array<BlockData?> by lazy {
        val index = arrayOfNulls<BlockData>(blockStateCount)
        for (block in this.blocks) {
            for (stateId in block.minStateId..block.maxStateId) {
                if (stateId in index.indices) index[stateId] = block
            }
        }
        index
    }

    private val itemsById: Map<Int, ItemData> by lazy { this.items.associateBy(ItemData::id) }

    private val itemsByName: Map<String, ItemData> by lazy { this.items.associateBy(ItemData::name) }

    private val entitiesById: Map<Int, EntityData> by lazy { this.entities.associateBy(EntityData::id) }

    private val entitiesByName: Map<String, EntityData> by lazy { this.entities.associateBy(EntityData::name) }

    fun block(id: Int): BlockData? = blocksById[id]

    fun block(name: String): BlockData? = blocksByName[normalize(name)]

    fun blockByStateId(stateId: Int): BlockData? =
        if (stateId in blocksByState.indices) blocksByState[stateId] else null

    fun item(id: Int): ItemData? = itemsById[id]

    fun item(name: String): ItemData? = itemsByName[normalize(name)]

    fun entity(id: Int): EntityData? = entitiesById[id]

    fun entity(name: String): EntityData? = entitiesByName[normalize(name)]

    fun requireBlock(id: Int): BlockData = block(id) ?: missing("block", id.toString())

    fun requireBlock(name: String): BlockData = block(name) ?: missing("block", name)

    fun requireBlockByStateId(stateId: Int): BlockData =
        blockByStateId(stateId) ?: missing("block state", stateId.toString())

    fun requireItem(id: Int): ItemData = item(id) ?: missing("item", id.toString())

    fun requireItem(name: String): ItemData = item(name) ?: missing("item", name)

    fun requireEntity(id: Int): EntityData = entity(id) ?: missing("entity", id.toString())

    fun requireEntity(name: String): EntityData = entity(name) ?: missing("entity", name)

    fun blockState(name: String): Int? {
        val bracket = name.indexOf('[')
        if (bracket < 0) return block(name)?.defaultState
        val block = block(name.substring(0, bracket)) ?: return null
        val body = name.substring(bracket + 1).removeSuffix("]")
        if (body.isBlank()) return block.defaultState
        var stateId = block.defaultState
        for (entry in body.split(',')) {
            val separator = entry.indexOf('=')
            if (separator < 0) continue
            val property = block.property(entry.substring(0, separator).trim()) ?: continue
            stateId = block.withValue(stateId, property, entry.substring(separator + 1).trim())
        }
        return stateId
    }

    private fun normalize(name: String): String = name.trim().lowercase().removePrefix("minecraft:")

    private fun missing(kind: String, key: String): Nothing =
        throw MinecraftDataException("Minecraft $minecraftVersion has no $kind '$key'.")

    override fun toString(): String = "MinecraftData($minecraftVersion)"
}
