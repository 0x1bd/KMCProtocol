package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.network.Direction

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PacketMetadata(
    val id: Int,
    val direction: Direction
)
