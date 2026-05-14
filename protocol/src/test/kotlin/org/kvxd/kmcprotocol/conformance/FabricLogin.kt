package org.kvxd.kmcprotocol.conformance

import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.client.Client
import org.kvxd.kmcprotocol.packets.generated.handshake.serverbound.ServerboundSetProtocolPacket
import org.kvxd.kmcprotocol.packets.generated.login.clientbound.ClientboundCompressPacket
import org.kvxd.kmcprotocol.packets.generated.login.clientbound.ClientboundSuccessPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginAcknowledgedPacket
import org.kvxd.kmcprotocol.packets.generated.login.serverbound.ServerboundLoginStartPacket

internal suspend fun Client.loginToConfiguration(
    host: String,
    port: Int,
) {
    send(ServerboundSetProtocolPacket(FabricServerFixture.protocolVersion(), host, port, 2))
    send(
        ServerboundLoginStartPacket(
            username = "KmcProtocol",
            playerUUID = "00000000-0000-0000-0000-000000000001",
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
