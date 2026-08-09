package org.kvxd.kmcprotocol.data

class ItemData(
    val id: Int,
    val name: String,
    val displayName: String,
    val stackSize: Int,
    val maxDurability: Int? = null,
    val enchantCategories: List<String> = emptyList(),
    val repairWith: List<String> = emptyList(),
    val variations: List<Variation> = emptyList(),
) {
    override fun toString(): String = name
}
