package de.kvxd.kmcprotocol.packets.login.serverbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.*
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

@PacketMetadata(
    id = 0x01,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.LOGIN,
)
data class ServerboundEncryptionResponsePacket(
    val sharedSecret: Array<Byte>,
    val verifyToken: Array<Byte>,
) : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ServerboundEncryptionResponsePacket> {
            element(ServerboundEncryptionResponsePacket::sharedSecret, prefixedArray(ByteCodec))
            element(ServerboundEncryptionResponsePacket::verifyToken, prefixedArray(ByteCodec))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServerboundEncryptionResponsePacket

        if (!sharedSecret.contentEquals(other.sharedSecret)) return false
        if (!verifyToken.contentEquals(other.verifyToken)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sharedSecret.contentHashCode()
        result = 31 * result + verifyToken.contentHashCode()
        return result
    }

}