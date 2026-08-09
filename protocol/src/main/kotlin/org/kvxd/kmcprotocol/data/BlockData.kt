package org.kvxd.kmcprotocol.data

enum class BoundingBox {
    Block,
    Empty,
    Unknown,
}

enum class StatePropertyType {
    Bool,
    Int,
    Enum,
}

class BlockStateProperty(
    val name: String,
    val type: StatePropertyType,
    val numValues: Int,
    val values: List<String>,
    val stride: Int,
) {
    fun indexOf(value: String): Int = values.indexOf(value)

    override fun toString(): String = "$name=${values.joinToString(",")}"
}

class BlockDrop(
    val itemId: Int,
    val metadata: Int? = null,
    val minCount: Double? = null,
    val maxCount: Double? = null,
)

class Variation(
    val metadata: Int,
    val displayName: String,
    val description: String? = null,
)

class BlockData(
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
    val boundingBox: BoundingBox,
    val states: List<BlockStateProperty> = emptyList(),
    val harvestTools: Set<Int> = emptySet(),
    val drops: List<BlockDrop> = emptyList(),
    val variations: List<Variation> = emptyList(),
) {
    val stateCount: Int get() = maxStateId - minStateId + 1

    val materials: List<String> by lazy {
        material?.split(';')?.filter(String::isNotEmpty) ?: emptyList()
    }

    private val statesByName: Map<String, BlockStateProperty> by lazy {
        states.associateBy(BlockStateProperty::name)
    }

    fun property(name: String): BlockStateProperty? = statesByName[name]

    fun requireProperty(name: String): BlockStateProperty =
        property(name) ?: throw MinecraftDataException("Block $this has no state property '$name'.")

    fun valueIndex(stateId: Int, property: BlockStateProperty): Int =
        ((stateId - minStateId) / property.stride) % property.numValues

    fun value(stateId: Int, property: BlockStateProperty): String =
        property.values[valueIndex(stateId, property)]

    fun withIndex(stateId: Int, property: BlockStateProperty, index: Int): Int =
        stateId + (index - valueIndex(stateId, property)) * property.stride

    fun withValue(stateId: Int, property: BlockStateProperty, value: String): Int {
        val index = property.indexOf(value)
        return if (index < 0) stateId else withIndex(stateId, property, index)
    }

    fun stateOf(values: Map<String, String>): Int {
        var stateId = minStateId
        for (property in states) {
            val index = values[property.name]?.let(property::indexOf) ?: continue
            if (index >= 0) stateId += index * property.stride
        }
        return stateId
    }

    fun propertiesOf(stateId: Int): Map<String, String> {
        if (states.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>(states.size)
        for (property in states) result[property.name] = value(stateId, property)
        return result
    }

    fun describe(stateId: Int): String {
        val properties = propertiesOf(stateId)
        if (properties.isEmpty()) return name
        return name + properties.entries.joinToString(",", "[", "]") { "${it.key}=${it.value}" }
    }

    override fun toString(): String = name
}
