package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

class ProtocolData {

    val serializersModule = EmptySerializersModule()

    var state = ProtocolState.Handshake

    @OptIn(InternalSerializationApi::class)
    fun getPacketClassById(id: Int): KSerializer<MinecraftPacket>? {
        state.packets.forEach { packetClass ->
            val metadata = packetClass.annotations.filterIsInstance<PacketMetadata>().firstOrNull()

            if (metadata?.id != id) return null

            return packetClass.serializer() as KSerializer<MinecraftPacket>
        }

        return null
    }

}