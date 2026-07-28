package org.kvxd.kmcprotocol.conformance

import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.clientbound.ClientboundCompressPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.clientbound.ClientboundSuccessPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.serverbound.ServerboundLoginAcknowledgedPacket
import org.kvxd.kmcprotocol.packets.generated.v1_21_4.login.serverbound.ServerboundLoginStartPacket

internal suspend fun Client.loginToConfiguration(
    host: String,
    port: Int,
) {
    send(ServerboundSetProtocolPacket(FabricServerFixture.protocolVersion(), host, port, 2))
    send(
        ServerboundLoginStartPacket(
            username = "KmcProtocol",
            playerUUID = java.util.UUID(0L, 1L),
        )
    )

    var loggedIn = false
    while (!loggedIn) {
        when (receive()) {
            is ClientboundCompressPacket -> Unit
            is ClientboundSuccessPacket -> loggedIn = true
        }
    }

    send(ServerboundLoginAcknowledgedPacket)
    check(data.state == ProtocolState.Configuration) {
        "Expected login acknowledgement to move client to configuration, got ${data.state}"
    }
}
