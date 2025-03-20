package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import java.util.*
import kotlin.reflect.KProperty1

internal suspend fun ByteWriteChannel.writeUUID(value: UUID) {
    writeLong(value.mostSignificantBits)
    writeLong(value.leastSignificantBits)
}

internal suspend fun ByteReadChannel.readUUID(): UUID {
    return UUID(
        readLong(),
        readLong()
    )
}

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.uuid(
    property: KProperty1<T, UUID>
) {
    addCodec<UUID>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeUUID(value)
        },
        decoder = { channel -> channel.readUUID() }
    )
}