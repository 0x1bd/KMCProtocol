package org.kvxd.kmcprotocol.build

internal fun protocolStates(): List<ProtocolStateSource> =
    listOf(
        ProtocolStateSource("handshaking", "Handshake", "handshake"),
        ProtocolStateSource("status", "Status", "status"),
        ProtocolStateSource("login", "Login", "login"),
        ProtocolStateSource("configuration", "Configuration", "configuration"),
        ProtocolStateSource("play", "Play", "play"),
    )

internal fun protocolDirections(serverboundPrefix: String, clientboundPrefix: String): List<DirectionSource> =
    listOf(
        DirectionSource("toServer", serverboundPrefix, "serverbound", "Serverbound"),
        DirectionSource("toClient", clientboundPrefix, "clientbound", "Clientbound"),
    )