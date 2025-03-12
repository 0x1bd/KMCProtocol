package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.Direction
import de.kvxd.kmcprotocol.ProtocolState

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PacketMetadata(
    val id: Int,
    val direction: Direction,
    val state: ProtocolState
)
