package org.kvxd.kmcprotocol.network

/**
 * Packet direction relative to the Minecraft server.
 */
enum class Direction {

    /**
     * A packet sent by a client and received by a server.
     */
    Serverbound,

    /**
     * A packet sent by a server and received by a client.
     */
    Clientbound

}
