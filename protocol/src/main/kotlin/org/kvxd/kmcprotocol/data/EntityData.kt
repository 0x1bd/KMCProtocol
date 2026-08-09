package org.kvxd.kmcprotocol.data

class EntityData(
    val id: Int,
    val name: String,
    val displayName: String,
    val type: String,
    val internalId: Int? = null,
    val category: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val metadataKeys: List<String> = emptyList(),
) {
    override fun toString(): String = name
}
