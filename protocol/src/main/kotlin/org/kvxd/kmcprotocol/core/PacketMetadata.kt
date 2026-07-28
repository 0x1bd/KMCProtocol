package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PacketMetadata(
    val id: Int,

    val direction: Direction
)
