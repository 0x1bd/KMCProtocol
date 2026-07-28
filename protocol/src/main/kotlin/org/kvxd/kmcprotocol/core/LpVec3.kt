package org.kvxd.kmcprotocol.core

import kotlinx.serialization.Serializable

@Serializable
data class LpVec3(
    val x: Double,
    val y: Double,
    val z: Double,
)
