package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.packet.Direction

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PacketMetadata(
    val id: Int,
    val direction: Direction,
    val state: ProtocolState
)

