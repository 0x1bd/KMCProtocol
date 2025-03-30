package de.kvxd.kmcprotocol.packets.login.clientbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.*
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

@PacketMetadata(
    id = 0x01,
    direction = Direction.CLIENTBOUND,
    state = ProtocolState.LOGIN,
)
data class ClientboundEncryptionRequestPacket(
    val serverID: String = "",
    val publicKey: Array<Byte>,
    val verifyToken: Array<Byte>,
    val shouldAuthenticate: Boolean = false,
) : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ClientboundEncryptionRequestPacket> {
            element(ClientboundEncryptionRequestPacket::serverID, StringCodec)
            element(ClientboundEncryptionRequestPacket::publicKey, prefixedArray(ByteCodec))
            element(ClientboundEncryptionRequestPacket::verifyToken, prefixedArray(ByteCodec))
            element(ClientboundEncryptionRequestPacket::shouldAuthenticate, BooleanCodec)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClientboundEncryptionRequestPacket

        if (shouldAuthenticate != other.shouldAuthenticate) return false
        if (serverID != other.serverID) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!verifyToken.contentEquals(other.verifyToken)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shouldAuthenticate.hashCode()
        result = 31 * result + serverID.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + verifyToken.contentHashCode()
        return result
    }

}