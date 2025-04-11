package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.packets.handshake.IntentionPacket
import kotlin.reflect.KClass

enum class ProtocolState(
    val packets: List<KClass<out MinecraftPacket>>
) {

    Handshake(
        listOf(
            IntentionPacket::class
        )
    )

}