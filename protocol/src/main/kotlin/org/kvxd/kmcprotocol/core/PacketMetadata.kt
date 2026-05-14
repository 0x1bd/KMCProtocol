package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction

/**
 * Associates a packet class with its protocol id and direction.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PacketMetadata(
    /**
     * Numeric packet id for the containing protocol state.
     */
    val id: Int,

    /**
     * Direction in which this packet travels.
     */
    val direction: Direction
)
