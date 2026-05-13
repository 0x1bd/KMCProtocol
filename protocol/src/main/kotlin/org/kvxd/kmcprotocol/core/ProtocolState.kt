package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.generated.GeneratedPacketRegistry
import kotlin.reflect.KClass

enum class ProtocolState(
    val packets: List<KClass<out MinecraftPacket>>
) {

    Handshake(GeneratedPacketRegistry.handshake),
    Status(GeneratedPacketRegistry.status),
    Login(GeneratedPacketRegistry.login),
    Configuration(GeneratedPacketRegistry.configuration),
    Play(GeneratedPacketRegistry.play)

}
