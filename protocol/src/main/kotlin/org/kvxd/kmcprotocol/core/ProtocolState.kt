package org.kvxd.kmcprotocol.core

/**
 * Minecraft protocol state used to resolve packet ids and serializers.
 */
enum class ProtocolState {
    /**
     * Initial state used for the set-protocol handshake packet.
     */
    Handshake,

    /**
     * Server list ping and latency state.
     */
    Status,

    /**
     * Authentication and login negotiation state.
     */
    Login,

    /**
     * Registry and feature negotiation state before play begins.
     */
    Configuration,

    /**
     * In-game packet state.
     */
    Play
}
