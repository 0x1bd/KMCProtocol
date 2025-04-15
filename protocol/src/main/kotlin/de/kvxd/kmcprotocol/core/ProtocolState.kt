package de.kvxd.kmcprotocol.core

import kotlin.reflect.KClass

enum class ProtocolState(
    val packets: List<KClass<out MinecraftPacket>>
) {

    Handshake(
        listOf(
            //IntentionPacket::class
        )
    ),
    Status(
        listOf(
            //ServerboundStatusRequestPacket::class,
            //ClientboundStatusResponsePacket::class,
            //ServerboundPingRequestPacket::class,
            //ClientboundPongResponsePacket::class
        )
    ),
    Login(listOf())

}